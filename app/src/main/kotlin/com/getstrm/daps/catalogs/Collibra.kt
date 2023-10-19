package com.getstrm.daps.catalogs

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.apollographql.apollo3.ApolloClient
import com.collibra.generated.ListPhysicalDataAssetsQuery
import com.collibra.generated.ListSchemaIdsQuery
import com.collibra.generated.ListTablesInSchemaQuery
import com.collibra.generated.TableWithColumnsQuery
import com.getstrm.daps.config.CatalogConfiguration
import com.getstrm.daps.domain.DataCatalog
import java.util.*

class CollibraCatalog(config: CatalogConfiguration) : DataCatalog() {
    private val client = config.apolloClient()
    override fun close() {
        client.close()
    }

    override suspend fun listDatabases(): List<DataCatalog.Database> = listPhysicalAssets(AssetTypes.DATABASE).map {
        Database(this, it.id, it.getDataSourceType())
    }

    class Database(private val catalog: CollibraCatalog, id: String, dbType: String) :
        DataCatalog.Database(id, dbType) {
        constructor(catalog: CollibraCatalog, id: Any, dbType: String) : this(catalog, id.toString(), dbType)

        override suspend fun getSchemas(): List<DataCatalog.Schema> {
            val assets = catalog.client.query(ListSchemaIdsQuery(id)).execute().data!!.assets!!.filterNotNull()
                .flatMap { schema ->
                    schema.schemas
                }
            return assets.map {
                Schema(catalog, this, it.target.id.toString(), it.target.fullName)
            }
        }
    }

    class Schema(private val catalog: CollibraCatalog, database: DataCatalog.Database, id: String, name: String) :
        DataCatalog.Schema(database, id, name) {
        override suspend fun getTables(): List<DataCatalog.Table> =
            catalog.client.query(ListTablesInSchemaQuery(id)).execute().data!!.assets!!.filterNotNull()
                .flatMap { table ->
                    table.tables.map { Table(catalog, this, it.target.id.toString(), it.target.fullName) }
                }
    }

    class Table(private val catalog: CollibraCatalog, schema: DataCatalog.Schema, id: String, name: String) :
        DataCatalog.Table(schema, id, name) {
        override suspend fun getDataPolicy(): DataPolicy? {
            val response = catalog.client.query(TableWithColumnsQuery(id = id)).execute()
            return response.data?.tables?.firstOrNull()?.let { table ->
                val systemName =
                    table.schema.firstOrNull()?.schemaDetails?.database?.firstOrNull()?.databaseDetails?.domain?.name

                val builder = DataPolicy.newBuilder()
                builder.infoBuilder.title = table.displayName
                builder.infoBuilder.description = systemName
                builder.sourceBuilder.addAllAttributes(table.columns.map { it.toAttribute() })
                builder.build()
            }
        }

        private fun TableWithColumnsQuery.Column.toAttribute(): DataPolicy.Attribute {
            return with(DataPolicy.Attribute.newBuilder()) {
                addPathComponents(columnDetails.displayName)
                val sourceType = columnDetails.dataType.firstOrNull()?.value ?: "unknown"
                // source type mapping
                type = sourceType
                build()
            }
        }
    }

    class Configuration(
        private val serverUrl: String = "https://test-drive.collibra.com/graphql/knowledgeGraph/v1",
        private val username: String = "test-drive-user-9b8o5m7l",
        private val password: String = "Egwrazg\$8q3j6i0b",
    ) : DataCatalog.Configuration() {
        fun apolloClient(): ApolloClient {
            val basicAuth = Base64.getEncoder().encodeToString("$username:$password".toByteArray())

            return ApolloClient.Builder().serverUrl(serverUrl).addHttpHeader("Authorization", "Basic $basicAuth")
                .build()
        }
    }

    private suspend fun listPhysicalAssets(type: AssetTypes): List<ListPhysicalDataAssetsQuery.Asset> =
        client.query(ListPhysicalDataAssetsQuery(assetType = type.assetName)).execute().data!!.assets?.filterNotNull()
            ?: emptyList()

    private fun ListPhysicalDataAssetsQuery.Asset.getDataSourceType(): String =
        stringAttributes.find { it.type.publicId == "DataSourceType" }?.stringValue ?: "unknown"
}

enum class AssetTypes(val assetName: String) {
    DATABASE("Database"), SCHEMA("Schema"), TABLE("Table"), COLUMN("Column"),
}
