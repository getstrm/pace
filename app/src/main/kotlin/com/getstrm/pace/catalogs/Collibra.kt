package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.apollographql.apollo3.ApolloClient
import com.collibra.generated.*
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.BadRequestException.Code.INVALID_ARGUMENT
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.withUnknownTotals
import com.google.rpc.BadRequest
import java.util.*

class CollibraCatalog(config: CatalogConfiguration) : DataCatalog(config) {

    val client = apolloClient()
    override fun close() = client.close()

    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<DataCatalog.Database> =
        listPhysicalAssets(AssetTypes.DATABASE, pageParameters)
            .withUnknownTotals()
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

    inner class Database(override val catalog: CollibraCatalog, id: String, dbType: String, displayName: String) :
        DataCatalog.Database(catalog, id, dbType, displayName) {

        override suspend fun listSchemas(pageParameters: PageParameters): PagedCollection<DataCatalog.Schema> {
            // TODO handle errors gracefully
            val schemas = client.query(ListSchemaIdsQuery(id, pageParameters.skip, pageParameters.pageSize))
                .execute().data!!.assets!!.filterNotNull()
                .flatMap { it.schemas }
            return schemas.map {
                Schema(catalog, this, it.target.id.toString(), it.target.fullName)
            }.withUnknownTotals()
        }

        override suspend fun getSchema(schemaId: String): DataCatalog.Schema {
            // TODO handle errors gracefully
            val schemaAsset =
                catalog.client.query(GetSchemaQuery(schemaId)).execute().data!!.assets!!.filterNotNull().first()
            return Schema(catalog, this, schemaAsset.id.toString(), schemaAsset.fullName)
        }
    }

    inner class Schema(private val catalog: CollibraCatalog, database: DataCatalog.Database, id: String, name: String) :
        DataCatalog.Schema(database, id, name) {
        override suspend fun listTables(pageParameters: PageParameters): PagedCollection<DataCatalog.Table> {
            // TODO handle errors gracefully
            val tables =
                client.query(ListTablesInSchemaQuery(id, pageParameters.skip, pageParameters.pageSize))
                    .execute().data!!.assets!!.filterNotNull()
                    .flatMap { table ->
                        table.tables.map { Table(catalog, this, it.target.id.toString(), it.target.fullName) }
                    }
            return tables.withUnknownTotals()
        }

        override suspend fun getTable(tableId: String): DataCatalog.Table {
            val response = catalog.client.query(GetTableQuery(tableId)).execute()
            if (!response.errors.isNullOrEmpty()) {
                throw BadRequestException(
                    INVALID_ARGUMENT,
                    BadRequest.newBuilder()
                        .build(),
                    errorMessage = response.errors!!.joinToString { it.message }
                )
            }
            // TODO handle errors gracefully
            val table = response.data!!.assets!!.filterNotNull().firstOrNull() ?:
            throw BadRequestException(
                INVALID_ARGUMENT, BadRequest.getDefaultInstance(),
                errorMessage = response.errors?.joinToString { it.message } ?: "table $tableId not found"
            )
            return Table(catalog, this, tableId, table.fullName)
        }
    }

    class Table(private val catalog: CollibraCatalog, schema: DataCatalog.Schema, id: String, name: String) :
        DataCatalog.Table(schema, id, name) {

        override suspend fun createBlueprint(): DataPolicy? {
            // FIXME loop when more results. we don't know for sure we receive all the columns!
            val query = ColumnTypesAndTagsQuery(tableId = id, pageSize = 1000, skip = 0)
            val response = catalog.client.query(query).execute()
            if (!response.errors.isNullOrEmpty()) {
                throw BadRequestException(
                    INVALID_ARGUMENT,
                    BadRequest.newBuilder()
                        .build(),
                    errorMessage = response.errors?.joinToString { it.message }?:"unknown error"
                )
            }
            return with( DataPolicy.newBuilder()) {
                metadataBuilder.title = name
                metadataBuilder.description = schema.database.displayName
                response.data?.columns?.filterNotNull()?.forEach { column ->
                    sourceBuilder.addFields(column.toField())
                }
                build()
            }
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
        // TODO handle errors gracefully
        client.query(
            ListPhysicalDataAssetsQuery(
                assetType = type.assetName,
                skip = pageParameters?.skip ?: 0,
                pageSize = pageParameters?.pageSize ?: 10
            )
        ).execute().data!!.assets?.filterNotNull() ?: emptyList()

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
