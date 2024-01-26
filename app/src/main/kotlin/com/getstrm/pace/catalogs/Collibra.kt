package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Operation
import com.collibra.generated.ColumnTypesAndTagsQuery
import com.collibra.generated.GetDataBaseQuery
import com.collibra.generated.GetSchemaQuery
import com.collibra.generated.GetTableQuery
import com.collibra.generated.ListPhysicalDataAssetsQuery
import com.collibra.generated.ListSchemaIdsQuery
import com.collibra.generated.ListTablesInSchemaQuery
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.domain.Resource
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.MILLION_RECORDS
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.firstNonNull
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.onlyNonNulls
import com.getstrm.pace.util.pagedCalls
import com.getstrm.pace.util.withUnknownTotals
import com.google.rpc.DebugInfo
import com.google.rpc.ResourceInfo
import java.util.*
import kotlinx.coroutines.flow.single
import org.slf4j.LoggerFactory

class CollibraCatalog(config: CatalogConfiguration) : DataCatalog(config) {

    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    val client = apolloClient()

    override fun close() = client.close()

    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<Resource> =
        pagedCalls(pageParameters) { skip: Int, pageSize: Int ->
                client
                    .query(
                        ListPhysicalDataAssetsQuery(
                            assetType = AssetTypes.DATABASE.assetName,
                            skip = skip,
                            pageSize = pageSize
                        )
                    )
                    .executeOrThrowError()
                    .assets
                    .onlyNonNulls()
            }
            .withUnknownTotals()
            .map { Database(this, it.id.toString(), it.getDataSourceType(), it.displayName ?: "") }

    override suspend fun getDatabase(databaseId: String): DataCatalog.Database {
        // FIXME Catch Not Found error and translate
        val database =
            client.query(GetDataBaseQuery(databaseId)).executeOrThrowError().assets.firstNonNull()
        return Database(
            catalog = this,
            id = database.id.toString(),
            dbType = database.getDataSourceType(),
            displayName = database.displayName ?: ""
        )
    }

    inner class Database(
        override val catalog: CollibraCatalog,
        id: String,
        dbType: String,
        displayName: String
    ) : DataCatalog.Database(catalog, id, dbType, displayName) {

        override fun fqn(): String = id

        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> {
            val schemas =
                pagedCalls(pageParameters) { skip, pageSize ->
                    client
                        .query(ListSchemaIdsQuery(id, skip, pageSize))
                        .executeOrThrowError()
                        .assets
                        .onlyNonNulls()
                        .first()
                        .schemas
                }
            return schemas
                .map { Schema(catalog, this, it.target.id.toString(), it.target.fullName) }
                .withUnknownTotals()
        }

        override suspend fun getChild(childId: String): Resource {
            val schemaAsset =
                catalog.client
                    .query(GetSchemaQuery(childId))
                    .executeOrThrowError()
                    .assets
                    ?.firstOrNull()
                    ?: throw ResourceException(
                        ResourceException.Code.NOT_FOUND,
                        ResourceInfo.newBuilder()
                            .setResourceType("Schema")
                            .setResourceName(childId)
                            .setDescription("Schema $childId not found in database $id")
                            .setOwner("Database: $id")
                            .build(),
                        errorMessage = "schema $childId not found"
                    )
            return Schema(catalog, this, schemaAsset.id.toString(), schemaAsset.fullName)
        }
    }

    inner class Schema(
        private val catalog: CollibraCatalog,
        database: DataCatalog.Database,
        id: String,
        name: String
    ) : DataCatalog.Schema(database, id, name) {

        override fun fqn(): String = id

        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> {
            val tables =
                pagedCalls(pageParameters) { skip, pageSize ->
                        // first() refers to the single schema
                        client
                            .query(ListTablesInSchemaQuery(id, skip, pageSize))
                            .executeOrThrowError()
                            .assets
                            .onlyNonNulls()
                            .first()
                            .tables
                    }
                    .map { Table(catalog, this, it.target.id.toString(), it.target.fullName) }
            return tables.withUnknownTotals()
        }

        override suspend fun getChild(childId: String): DataCatalog.Table {
            val table =
                catalog.client
                    .query(GetTableQuery(childId))
                    .executeOrThrowError()
                    .assets
                    .onlyNonNulls()
                    .firstOrNull()
                    ?: throw ResourceException(
                        ResourceException.Code.NOT_FOUND,
                        ResourceInfo.newBuilder()
                            .setResourceType("Table")
                            .setResourceName(childId)
                            .setDescription("Table $childId not found in schema $id")
                            .setOwner("Schema: $id")
                            .build(),
                        errorMessage = "table $childId not found"
                    )
            return Table(catalog, this, childId, table.fullName)
        }
    }

    class Table(
        private val catalog: CollibraCatalog,
        schema: DataCatalog.Schema,
        id: String,
        name: String
    ) : DataCatalog.Table(schema, id, name) {

        override fun fqn(): String = id

        override suspend fun createBlueprint(): DataPolicy {
            val columns: List<ColumnTypesAndTagsQuery.Column> =
                pagedCalls(MILLION_RECORDS) { skip, pageSize ->
                    catalog.client
                        .query(
                            ColumnTypesAndTagsQuery(tableId = id, pageSize = pageSize, skip = skip)
                        )
                        .executeOrThrowError()
                        .columns
                        .onlyNonNulls()
                }
            return with(DataPolicy.newBuilder()) {
                metadataBuilder.title = displayName
                metadataBuilder.description = schema.database.displayName
                columns.forEach { column -> sourceBuilder.addFields(column.toField()) }
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
        val basicAuth =
            Base64.getEncoder()
                .encodeToString("${config.userName}:${config.password}".toByteArray())
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

/** utility function to provide error handling in grpc compatible format. */
private suspend fun <D : Operation.Data> ApolloCall<D>.executeOrThrowError(): D {
    val apolloResponse = toFlow().single()
    if (apolloResponse.hasErrors()) {
        throw InternalException(
            InternalException.Code.INTERNAL,
            DebugInfo.newBuilder()
                .setDetail(apolloResponse.errors?.joinToString() ?: "unknown")
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
