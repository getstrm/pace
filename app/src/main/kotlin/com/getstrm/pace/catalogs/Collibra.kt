package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Operation
import com.collibra.generated.*
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.BadRequestException.Code.INVALID_ARGUMENT
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.util.*
import com.google.rpc.BadRequest
import com.google.rpc.DebugInfo
import kotlinx.coroutines.flow.single
import org.slf4j.LoggerFactory
import java.util.*


class CollibraCatalog(config: CatalogConfiguration) : DataCatalog(config) {

    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    val client = apolloClient()
    override fun close() = client.close()

    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<DataCatalog.Database> =
        pagedCalls(pageParameters) { skip: Int, pageSize: Int ->
            client.query(
                ListPhysicalDataAssetsQuery(
                    assetType = AssetTypes.DATABASE.assetName,
                    skip = skip,
                    pageSize = pageSize
                )
            ).executeOrThrowError().assets.onlyNonNulls()
        }
            .withUnknownTotals()
            .map { Database(this, it.id.toString(), it.getDataSourceType(), it.displayName ?: "") }

    override suspend fun getDatabase(databaseId: String): DataCatalog.Database {
        val database = client.query(GetDataBaseQuery(databaseId)).executeOrThrowError().assets.firstNonNull()
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
            val schemas = pagedCalls(pageParameters) { skip, pageSize ->
                client.query(ListSchemaIdsQuery(id, skip, pageSize))
                    .executeOrThrowError().assets.onlyNonNulls().first().schemas
            }
            return schemas.map {
                Schema(catalog, this, it.target.id.toString(), it.target.fullName)
            }.withUnknownTotals()
        }

        override suspend fun getSchema(schemaId: String): DataCatalog.Schema {
            val schemaAsset =
                catalog.client.query(GetSchemaQuery(schemaId)).executeOrThrowError().assets.firstNonNull()
            return Schema(catalog, this, schemaAsset.id.toString(), schemaAsset.fullName)
        }
    }

    inner class Schema(private val catalog: CollibraCatalog, database: DataCatalog.Database, id: String, name: String) :
        DataCatalog.Schema(database, id, name) {
            
        override suspend fun listTables(pageParameters: PageParameters): PagedCollection<DataCatalog.Table> {
            val tables = pagedCalls(pageParameters) { skip, pageSize ->
                // first() refers to the single schema
                client.query(ListTablesInSchemaQuery(id, skip, pageSize))
                    .executeOrThrowError().assets.onlyNonNulls().first().tables
            }.map { Table(catalog, this, it.target.id.toString(), it.target.fullName) }
            return tables.withUnknownTotals()
        }

        override suspend fun getTable(tableId: String): DataCatalog.Table {
            val table = catalog.client.query(GetTableQuery(tableId)).executeOrThrowError().assets.onlyNonNulls().firstOrNull() ?:
            throw BadRequestException(
                INVALID_ARGUMENT, BadRequest.newBuilder()
                    .addFieldViolations(BadRequest.FieldViolation.newBuilder()
                        .setDescription("table $tableId not found")
                    )
                    .build()
                ,
                errorMessage = "table $tableId not found"
            )
            return Table(catalog, this, tableId, table.fullName)
        }
    }

    class Table(private val catalog: CollibraCatalog, schema: DataCatalog.Schema, id: String, name: String) :
        DataCatalog.Table(schema, id, name) {

        override suspend fun createBlueprint(): DataPolicy? {
            val columns: List<ColumnTypesAndTagsQuery.Column> = pagedCalls(MILLION_RECORDS)
            { skip, pageSize ->
                catalog.client.query(ColumnTypesAndTagsQuery(tableId = id, pageSize = pageSize, skip = skip))
                    .executeOrThrowError().columns.onlyNonNulls()
            }
            return with( DataPolicy.newBuilder()) {
                metadataBuilder.title = name
                metadataBuilder.description = schema.database.displayName
                columns.forEach { column ->
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

    private fun ListPhysicalDataAssetsQuery.Asset.getDataSourceType(): String =
        stringAttributes.find { it.type.publicId == "DataSourceType" }?.stringValue ?: "unknown"

    private fun GetDataBaseQuery.Asset.getDataSourceType(): String =
        stringAttributes.find { it.type.publicId == "DataSourceType" }?.stringValue ?: "unknown"
}

/**
 * utility function to provide error handling in grpc compatible format.
 */
private suspend fun <D : Operation.Data> ApolloCall<D>.executeOrThrowError(): D {
    val apolloResponse = toFlow().single()
    if(apolloResponse.hasErrors()){
        throw InternalException(
            InternalException.Code.INTERNAL,
            DebugInfo.newBuilder()
                .setDetail(apolloResponse.errors?.joinToString()?:"unknown")
                .build()
        )
    }
    return apolloResponse.dataAssertNoErrors
}

enum class AssetTypes(val assetName: String) {
    DATABASE("Database"),
    SCHEMA("Schema"),
    TABLE("Table"),
    COLUMN("Column"),
}
