package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageInfo
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.apollographql.apollo3.ApolloClient
import com.collibra.generated.*
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.util.*
import com.google.rpc.BadRequest
import java.util.*

class CollibraCatalog(config: CatalogConfiguration) : DataCatalog(config) {

    val client = apolloClient()
    override fun close() {
        client.close()
    }

    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<DataCatalog.Database> =
        listPhysicalAssets(AssetTypes.DATABASE, pageParameters)
            .withPageInfo(PageInfo.getDefaultInstance()) // TODO add page info
            .map { Database(this, it.id.toString(), it.getDataSourceType(), it.displayName ?: "") }

    override suspend fun getDatabase(databaseId: String): DataCatalog.Database {
        val database = client.query(GetDataBaseQuery(databaseId)).execute().data!!.assets!!.filterNotNull().first()
        return Database(
            catalog = this,
            id = database.id.toString(),
            dbType = database.getDataSourceType(),
            displayName = database.displayName ?: ""
        )
    }

    class Database(override val catalog: CollibraCatalog, id: String, dbType: String, displayName: String) :
        DataCatalog.Database(catalog, id, dbType, displayName) {

        override suspend fun listSchemas(pageParameters: PageParameters): PagedCollection<DataCatalog.Schema> {
            val assets = catalog.client.query(ListSchemaIdsQuery(id, pageParameters.skip, pageParameters.pageSize))
                .execute().data!!.assets!!.filterNotNull()
                .flatMap { schema ->
                    schema.schemas
                }
            return assets.map {
                Schema(catalog, this, it.target.id.toString(), it.target.fullName)
            }.withPageInfo()
        }

        override suspend fun getSchema(schemaId: String): DataCatalog.Schema {
            val schemaAsset =
                catalog.client.query(GetSchemaQuery(schemaId)).execute().data!!.assets!!.filterNotNull().first()
            return Schema(catalog, this, schemaAsset.id.toString(), schemaAsset.fullName)
        }
    }

    class Schema(private val catalog: CollibraCatalog, database: DataCatalog.Database, id: String, name: String) :
        DataCatalog.Schema(database, id, name) {
        override suspend fun listTables(pageParameters: PageParameters): PagedCollection<DataCatalog.Table> {
            val tables =
                catalog.client.query(ListTablesInSchemaQuery(id, pageParameters.skip, pageParameters.pageSize))
                    .execute().data!!.assets!!.filterNotNull()
                    .flatMap { table ->
                        table.tables.map { Table(catalog, this, it.target.id.toString(), it.target.fullName) }
                    }
            return tables.withUnknownTotals()
        }

        // TODO create graphql specific query
        override suspend fun getTable(tableId: String): DataCatalog.Table {
            return super.getTable(tableId)
        }
    }

    class Table(private val catalog: CollibraCatalog, schema: DataCatalog.Schema, id: String, name: String) :
        DataCatalog.Table(schema, id, name) {

        override suspend fun getDataPolicy(): DataPolicy? {
            // TODO loop when more results
            val query = ColumnTypesAndTagsQuery(tableId = id, pageSize = 1000, skip = 0)
            val response = catalog.client.query(query).execute()
            if (!response.errors.isNullOrEmpty()) {
                throw BadRequestException(
                    BadRequestException.Code.INVALID_ARGUMENT,
                    BadRequest.newBuilder()
                        .build(),
                    errorMessage = response.errors!!.joinToString { it.message }
                )

            }
            val builder = DataPolicy.newBuilder()
            builder.metadataBuilder.title = name
            builder.metadataBuilder.description = schema.database.displayName
            response.data?.columns?.filterNotNull()?.forEach { column ->
                builder.sourceBuilder.addFields(column.toField())
            }
            return builder.build()
        }

        private fun ColumnTypesAndTagsQuery.Column.toField(): DataPolicy.Field =
            with(DataPolicy.Field.newBuilder()) {
                addNameParts(displayName)
                val sourceType = dataType.firstOrNull()?.value ?: "unknown"
                // source type mapping
                type = sourceType
                addAllTags(tags.map { it.name })
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

    private suspend fun listPhysicalAssets(
        type: AssetTypes,
        pageParameters: PageParameters?
    ): List<ListPhysicalDataAssetsQuery.Asset> =
        client.query(
            ListPhysicalDataAssetsQuery(
                assetType = type.assetName,
                skip = pageParameters?.skip ?: 0,
                pageSize = pageParameters?.pageSize ?: 10
            )
        ).execute().data!!.assets?.filterNotNull()
            ?: emptyList()

    private fun ListPhysicalDataAssetsQuery.Asset.getDataSourceType(): String =
        stringAttributes.find { it.type.publicId == "DataSourceType" }?.stringValue ?: "unknown"

    private fun GetDataBaseQuery.Asset.getDataSourceType(): String =
        stringAttributes.find { it.type.publicId == "DataSourceType" }?.stringValue ?: "unknown"


}

enum class AssetTypes(val assetName: String) {
    DATABASE("Database"),
    SCHEMA("Schema"),
    TABLE("Table"),
    COLUMN("Column"),
}
