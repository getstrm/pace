package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.apollographql.apollo3.ApolloClient
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.domain.LeafResource
import com.getstrm.pace.domain.Resource
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.*
import com.google.rpc.ResourceInfo
import io.datahubproject.generated.GetDatasetDetailsQuery
import io.datahubproject.generated.ListDatasetsQuery
import org.slf4j.LoggerFactory

/**
 * interaction with Datahub.
 *
 * Datahub doesn't have a database→schema→table category. Instead we reply with just one database,
 * one schema, and a large set of tables.
 */
class DatahubCatalog(config: CatalogConfiguration) : DataCatalog(config) {
    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    val client = apolloClient()
    private val database = Database()
    private val schema = Schema()

    override suspend fun platformResourceName(index: Int): String {
        return listOf("datahub", "platform", "dataset").getOrElse(index) {
            super.platformResourceName(index)
        }
    }

    override fun close() {
        client.close()
    }

    override suspend fun listChildren(pageParameters: PageParameters): PagedCollection<Resource> =
        listOf(database).withPageInfo()

    override suspend fun getChild(childId: String): Resource = database

    private inner class Database : Resource {
        override val id: String = "datahub"
        override val displayName = id

        override fun fqn(): String = id

        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> = listOf(schema).withPageInfo()

        override suspend fun getChild(childId: String): Resource = schema
    }

    private inner class Schema : Resource {
        override val id = "schema"
        override val displayName = id

        override fun fqn(): String = id

        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> {
            val response =
                try {
                    client
                        .query(ListDatasetsQuery(pageParameters.skip, pageParameters.pageSize))
                        .execute()
                } catch (e: Exception) {
                    log.warn("error", e)
                    throw RuntimeException("Error fetching databases: ${e.message}")
                }
            if (response.hasErrors())
                throw RuntimeException("Error fetching databases: ${response.errors}")
            val tables =
                response.data?.search?.searchResults?.map { Table(it.entity.urn) }.orEmpty()
            return tables.withTotal(response.data?.search?.total ?: -1)
        }

        override suspend fun getChild(childId: String): Resource {
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
            return Table(dataset.urn)
        }
    }

    private inner class Table(urn: String) : LeafResource() {
        // this property is lazily filled in when calling getDataPolicy
        lateinit var dataset: GetDatasetDetailsQuery.Dataset
        override val id = urn
        override val displayName: String
            get() = urnPattern.matchEntire(id)?.groupValues?.last() ?: id

        override fun fqn(): String = id

        override suspend fun createBlueprint(): DataPolicy {
            val response = client.query(GetDatasetDetailsQuery(id)).execute()
            dataset = response.data!!.dataset!!
            if (response.hasErrors())
                throw RuntimeException("Error fetching details of dataset: ${response.errors}")
            val policyBuilder = DataPolicy.newBuilder()

            // tags don't exist in schemaMetadata but only in editableSchemaMetadata!
            val addtributeTags =
                dataset.editableSchemaMetadata?.editableSchemaFieldInfo?.associate { fieldInfo ->
                    fieldInfo.fieldPath to
                        fieldInfo.tags?.tags?.map { it.tag.properties?.name.orEmpty() }.orEmpty()
                } ?: emptyMap()

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
        // TODO re-implement pleasant displayName. Requires working datahub setup
        val urnPattern = """^urn:li:dataset:\(urn:li:dataPlatform:([^,]*),(.*)\)$""".toRegex()
        private val stripKafkaPrefix = """^(\[[^]]+\]\.)+""".toRegex()
    }
}
