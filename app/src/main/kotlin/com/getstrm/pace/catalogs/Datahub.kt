package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.apollographql.apollo3.ApolloClient
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.domain.Resource
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.*
import com.google.rpc.ResourceInfo
import io.datahubproject.generated.GetDatasetDetailsQuery
import io.datahubproject.generated.ListDatasetsQuery

/**
 * interaction with Datahub.
 *
 * Datahub doesn't have a database→schema→table category. Instead we reply with just one database,
 * one schema, and a large set of tables.
 */
class DatahubCatalog(config: CatalogConfiguration) : DataCatalog(config) {
    val client = apolloClient()
    val database = Database(this)
    val schema = Schema(database)

    override fun close() {
        client.close()
    }

    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<Resource> {
        return listOf(database).withPageInfo()
    }

    override suspend fun getDatabase(databaseId: String): DataCatalog.Database {
        return database
    }

    inner class Database(override val catalog: DatahubCatalog) :
        DataCatalog.Database(catalog, catalog.id, "datahub", catalog.id) {
        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> = listOf(schema).withPageInfo()

        override suspend fun getChild(childId: String): Resource {
            return schema
        }
    }

    inner class Schema(database: Database) :
        DataCatalog.Schema(database, database.id, database.id) {
        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> {
            val response =
                client
                    .query(ListDatasetsQuery(pageParameters.skip, pageParameters.pageSize))
                    .execute()
            if (response.hasErrors())
                throw RuntimeException("Error fetching databases: ${response.errors}")
            val tables =
                response.data?.search?.searchResults?.map { Table(this, it.entity.urn) }.orEmpty()
            return tables.withTotal(response.data?.search?.total ?: -1)
        }

        override suspend fun getChild(childId: String): DataCatalog.Table {
            val response = client.query(GetDatasetDetailsQuery(childId)).execute()
            val dataset =
                response.data!!.dataset
                    ?: throw ResourceException(
                        ResourceException.Code.NOT_FOUND,
                        ResourceInfo.newBuilder()
                            .setResourceType("Table")
                            .setResourceName(childId)
                            .setDescription("Table $childId not found in schema $id")
                            .setOwner("Schema: $id")
                            .build()
                    )
            return Table(this, dataset.urn)
        }
    }

    inner class Table(schema: Schema, urn: String) : DataCatalog.Table(schema, urn, urn) {
        // this property is lazily filled in when calling getDataPolicy
        lateinit var dataset: GetDatasetDetailsQuery.Dataset

        override suspend fun createBlueprint(): DataPolicy {
            val response = client.query(GetDatasetDetailsQuery(id)).execute()
            dataset = response.data!!.dataset!!
            if (response.hasErrors())
                throw RuntimeException("Error fetching details of dataset: ${response.errors}")
            val policyBuilder = DataPolicy.newBuilder()

            // tags don't exist in schemaMetadata but only in editableSchemaMetadata!
            val addtributeTags =
                dataset.editableSchemaMetadata
                    ?.editableSchemaFieldInfo
                    ?.map {
                        it.fieldPath to
                            it.tags?.tags?.map { it.tag.properties?.name.orEmpty() }.orEmpty()
                    }
                    ?.toMap() ?: emptyMap()

            policyBuilder.sourceBuilder.addAllFields(
                dataset.schemaMetadata?.fields?.map {
                    DataPolicy.Field.newBuilder()
                        .addAllNameParts(it.fieldPath.extractPathComponents())
                        .addAllTags(addtributeTags[it.fieldPath] ?: emptyList())
                        .setType(it.type.rawValue)
                        .setRequired(!it.nullable)
                        .build()
                        .normalizeType()
                } ?: emptyList(),
            )

            policyBuilder.metadataBuilder.title =
                dataset.platform.properties?.displayName ?: dataset.urn
            policyBuilder.metadataBuilder.description = dataset.platform.name
            policyBuilder.metadataBuilder.addAllTags(
                dataset.tags?.tags?.map { it.tag.properties?.name.orEmpty() }.orEmpty(),
            )

            return policyBuilder.build()
        }

        /**
         * Some fieldPaths in the Datahub sample data (e.g. Kafka), contain field paths that match
         * '[version=2.0].[type=boolean].field_bar', which is probably an export format of Kafka. We
         * need to skip the version and type here, as those are not actual path components.
         */
        private fun String.extractPathComponents() = this.replace(stripKafkaPrefix, "").split(".")
    }

    private fun apolloClient(): ApolloClient =
        ApolloClient.Builder()
            .serverUrl(config.serverUrl)
            .addHttpHeader("Authorization", "Bearer ${config.token}")
            .build()

    companion object {
        val urnPattern = """^urn:li:dataset:\(urn:li:dataPlatform:([^,]*),(.*)\)$""".toRegex()
        private val stripKafkaPrefix = """^(\[[^]]+\]\.)+""".toRegex()
    }
}
