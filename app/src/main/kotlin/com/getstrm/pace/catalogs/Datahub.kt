package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.apollographql.apollo3.ApolloClient
import com.getstrm.pace.config.CatalogConfiguration
import io.datahubproject.generated.GetDatasetDetailsQuery
import io.datahubproject.generated.ListDatasetsQuery
import com.getstrm.pace.util.normalizeType

class DatahubCatalog(config: CatalogConfiguration) : DataCatalog(config) {
    val client = apolloClient()

    override fun close() {
        client.close()
    }

    override suspend fun listDatabases(): List<Database> {
        return fetchAllDatasets().map {
            Database(this, it.entity.urn)
        }
    }

    private suspend fun fetchAllDatasets(start: Int = 0): List<ListDatasetsQuery.SearchResult> {
        val response = client.query(ListDatasetsQuery(start, config.fetchSize ?: 1)).execute()

        if (response.hasErrors()) throw RuntimeException("Error fetching databases: ${response.errors}")

        val resultSize = response.data?.search?.searchResults?.size ?: 0

        return if (resultSize == 0) {
            emptyList()
        } else if (resultSize < (config.fetchSize ?: 1)) {
            response.data?.search?.searchResults ?: emptyList()
        } else {
            response.data?.search?.searchResults?.plus(fetchAllDatasets(start + (config.fetchSize ?: 1))) ?: emptyList()
        }
    }

    class Database(override val catalog: DatahubCatalog, id: String) : DataCatalog.Database(catalog, id) {
        override suspend fun getSchemas(): List<DataCatalog.Schema> {
            return catalog.client.query(GetDatasetDetailsQuery(id)).execute().data?.dataset?.let { dataset ->
                val schema = Schema(this, dataset)
                listOf(schema)
            } ?: emptyList()
        }
    }

    class Schema(database: Database, private val dataset: GetDatasetDetailsQuery.Dataset) :
        DataCatalog.Schema(database, dataset.urn, dataset.platform.properties?.displayName ?: dataset.urn) {
        override suspend fun getTables(): List<DataCatalog.Table> = listOf(Table(this, dataset))
    }

    class Table(schema: Schema, private val dataset: GetDatasetDetailsQuery.Dataset) : DataCatalog.Table(schema, dataset.urn, dataset.platform.properties?.displayName ?: dataset.urn) {
        override suspend fun getDataPolicy(): DataPolicy? {
            val policyBuilder = DataPolicy.newBuilder()

            // tags don't exist in schemaMetadata but only in editableSchemaMetadata!
            val addtributeTags = dataset.editableSchemaMetadata?.editableSchemaFieldInfo?.map {
                it.fieldPath to it.tags?.tags?.map { it.tag.properties?.name.orEmpty() }.orEmpty()
            }?.toMap() ?: emptyMap()

            policyBuilder.sourceBuilder.addAllFields(
                dataset.schemaMetadata?.fields?.map {
                    DataPolicy.Field.newBuilder()
                        .addAllNameParts(it.fieldPath.extractPathComponents())
                        .addAllTags(addtributeTags[it.fieldPath] ?: emptyList())
                        .setType(it.type.rawValue)
                        .setRequired(!it.nullable)
                        .build().normalizeType()
                } ?: emptyList(),
            )

            policyBuilder.metadataBuilder.title = dataset.platform.properties?.displayName ?: dataset.urn
            policyBuilder.metadataBuilder.description = dataset.platform.name
            policyBuilder.metadataBuilder.addAllTags(
                dataset.tags?.tags?.map { it.tag.properties?.name.orEmpty() }.orEmpty(),
            )

            return policyBuilder.build()
        }

        /**
         * Some fieldPaths in the Datahub sample data (e.g. Kafka), contain field paths that match '[version=2.0].[type=boolean].field_bar', which is probably an export format of Kafka. We need to skip the version and type here, as those are not actual path components.
         */
        private fun String.extractPathComponents() = this.replace(stripKafkaPrefix, "").split(".")

        companion object {
            private val stripKafkaPrefix = """^(\[[^]]+\]\.)+""".toRegex()
        }
    }
    private fun apolloClient(): ApolloClient = ApolloClient.Builder()
        .serverUrl(config.serverUrl)
        .addHttpHeader("Authorization", "Bearer ${config.token}")
        .build()
}
