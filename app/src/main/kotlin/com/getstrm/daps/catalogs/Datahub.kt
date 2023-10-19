package com.getstrm.daps.catalogs

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.apollographql.apollo3.ApolloClient
import com.getstrm.daps.config.CatalogConfiguration
import com.getstrm.daps.domain.DataCatalog
import io.datahubproject.generated.GetDatasetDetailsQuery
import io.datahubproject.generated.ListDatasetsQuery

class DatahubCatalog(private val config: CatalogConfiguration = DatahubConfiguration()) : DataCatalog() {
    val client = config.apolloClient()

    override fun close() {
        client.close()
    }

    override suspend fun listDatabases(): List<Database> {
        return fetchAllDatasets().map {
            Database(this, it.entity.urn)
        }
    }

    private suspend fun fetchAllDatasets(start: Int = 0): List<ListDatasetsQuery.SearchResult> {
        val response = client.query(ListDatasetsQuery(start, config.fetchSize)).execute()

        if (response.hasErrors()) throw RuntimeException("Error fetching databases: ${response.errors}")

        val resultSize = response.data?.search?.searchResults?.size ?: 0

        return if (resultSize == 0) {
            emptyList()
        } else if (resultSize < config.fetchSize) {
            response.data?.search?.searchResults ?: emptyList()
        } else {
            response.data?.search?.searchResults?.plus(fetchAllDatasets(start + config.fetchSize)) ?: emptyList()
        }
    }

    class Database(private val catalog: DatahubCatalog, id: String) : DataCatalog.Database(id) {
        override suspend fun getSchemas(): List<DataCatalog.Schema> {
            return catalog.client.query(GetDatasetDetailsQuery(id)).execute().data?.dataset?.let { dataset ->
                val schema = Schema(this, dataset)
                listOf(schema)
            } ?: emptyList()
        }
    }

    class Schema(database: Database, private val dataset: GetDatasetDetailsQuery.Dataset) : DataCatalog.Schema(database, dataset.urn, dataset.platform.properties?.displayName ?: dataset.urn) {
        override suspend fun getTables(): List<DataCatalog.Table> = listOf(Table(this, dataset))
    }

    class Table(schema: Schema, private val dataset: GetDatasetDetailsQuery.Dataset) : DataCatalog.Table(schema, dataset.urn, dataset.platform.properties?.displayName ?: dataset.urn) {
        override suspend fun getDataPolicy(): DataPolicy? {
            val policyBuilder = DataPolicy.newBuilder()

            policyBuilder.sourceBuilder.addAllAttributes(
                dataset.schemaMetadata?.fields?.map {
                    DataPolicy.Attribute.newBuilder()
                        .addAllPathComponents(it.fieldPath.extractPathComponents())
                        .setType(it.type.rawValue)
                        .setRequired(!it.nullable)
                        .build()
                } ?: emptyList())

            policyBuilder.infoBuilder.title = dataset.platform.properties?.displayName ?: dataset.urn
            policyBuilder.infoBuilder.description = dataset.platform.name

            return policyBuilder.build()
        }

        /**
         * Some fieldPaths in the Datahub sample data (e.g. Kafka), contain field paths that match '[version=2.0].[type=boolean].field_bar', which is probably an export format of Kafka. We need to skip the version and type here, as those are not actual path components.
         */
        private fun String.extractPathComponents() = usefulFieldPathsRegex.find(this)?.let { match ->
            match.groupValues[1].split(".")
        } ?: this.split(".")

        companion object {
            private val usefulFieldPathsRegex = "\\.([^\\]]+)\$".toRegex()
        }

    }
}

class DatahubConfiguration(
    private val serverUrl: String = "http://datahub-datahub-frontend.datahub:9002/api/graphql",
    private val token: String = "eyJhbGciOiJIUzI1NiJ9.eyJhY3RvclR5cGUiOiJVU0VSIiwiYWN0b3JJZCI6ImRhdGFodWIiLCJ0eXBlIjoiUEVSU09OQUwiLCJ2ZXJzaW9uIjoiMiIsImp0aSI6IjE4YWExMjA3LWY2NTQtNDc4OS05MTU3LTkwYTMyMjExMWJkYyIsInN1YiI6ImRhdGFodWIiLCJpc3MiOiJkYXRhaHViLW1ldGFkYXRhLXNlcnZpY2UifQ.8-NksHdL4p3o9_Bryst2MOvH-bATl-avC8liB-E2_sM",
    val fetchSize: Int = 1
) : DataCatalog.Configuration() {
    fun apolloClient(): ApolloClient = ApolloClient.Builder()
        .serverUrl(serverUrl)
        .addHttpHeader("Authorization", "Bearer $token")
        .build()
}
