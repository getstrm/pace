package com.getstrm.pace.catalogs
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.apollographql.apollo3.ApolloClient
import com.collibra.generated.ListPhysicalDataAssetsQuery
import com.collibra.generated.ListSchemaIdsQuery
import com.collibra.generated.ListTablesInSchemaQuery
import com.collibra.generated.TableWithColumnsQuery
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.util.normalizeType
import java.util.*

class CollibraCatalog(config: CatalogConfiguration) : DataCatalog(config) {

    val client = apolloClient()
    override fun close() {
        client.close()
    }

    override suspend fun listDatabases(): List<DataCatalog.Database> =
        listPhysicalAssets(AssetTypes.DATABASE).map {
            Database(this, it.id, it.getDataSourceType())
        }

    class Database(override val catalog: CollibraCatalog, id: String, dbType: String) :
        DataCatalog.Database(catalog, id, dbType) {
        constructor(catalog: CollibraCatalog, id: Any, dbType: String) : this(catalog, id.toString(), dbType)

        override suspend fun getSchemas(): List<DataCatalog.Schema> {
            val assets = catalog.client.query(ListSchemaIdsQuery(id)).execute().data!!.assets!!.filterNotNull().flatMap { schema ->
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
            catalog.client.query(ListTablesInSchemaQuery(id)).execute().data!!.assets!!.filterNotNull().flatMap { table ->
                table.tables.map { Table(catalog, this, it.target.id.toString(), it.target.fullName) }
            }
    }

    class Table(private val catalog: CollibraCatalog, schema: DataCatalog.Schema, id: String, name: String) :
        DataCatalog.Table(schema, id, name) {
        override suspend fun getDataPolicy(): DataPolicy? {
            val response = catalog.client.query(TableWithColumnsQuery(id = id)).execute()
            return response.data?.tables?.firstOrNull()?.let { table ->
                val systemName = table.schema.firstOrNull()?.schemaDetails?.database?.firstOrNull()?.databaseDetails?.domain?.name

                val builder = DataPolicy.newBuilder()
                builder.metadataBuilder.title = table.displayName
                builder.metadataBuilder.description = systemName
                builder.sourceBuilder.addAllFields(table.columns.map { it.toField() })
                builder.build()
            }
        }

        private fun TableWithColumnsQuery.Column.toField(): DataPolicy.Field =
            with(DataPolicy.Field.newBuilder()) {
                addNameParts(columnDetails.displayName)
                val sourceType = columnDetails.dataType.firstOrNull()?.value ?: "unknown"
                // source type mapping
                type = sourceType
                addAllTags(columnDetails.tags.map { it.name })
                build().normalizeType()
            }
    }

    private fun apolloClient(): ApolloClient {
        val basicAuth = Base64.getEncoder().encodeToString("${config.userName}:${config.password}".toByteArray())
        return ApolloClient.Builder()
            .serverUrl(config.serverUrl)
            .addHttpHeader("Authorization", "Basic $basicAuth")
            .build()
    }
    private suspend fun listPhysicalAssets(type: AssetTypes): List<ListPhysicalDataAssetsQuery.Asset> =
        client.query(ListPhysicalDataAssetsQuery(assetType = type.assetName)).execute().data!!.assets?.filterNotNull() ?: emptyList()

    private fun ListPhysicalDataAssetsQuery.Asset.getDataSourceType(): String =
        stringAttributes.find { it.type.publicId == "DataSourceType" }?.stringValue ?: "unknown"
}

enum class AssetTypes(val assetName: String) {
    DATABASE("Database"),
    SCHEMA("Schema"),
    TABLE("Table"),
    COLUMN("Column"),
}
