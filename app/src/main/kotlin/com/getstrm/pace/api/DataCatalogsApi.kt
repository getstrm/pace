package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.*
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.service.DataCatalogsService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class DataCatalogsApi(
    private val dataCatalogsService: DataCatalogsService
) : DataCatalogsServiceGrpcKt.DataCatalogsServiceCoroutineImplBase() {

    override suspend fun listCatalogs(request: ListCatalogsRequest): ListCatalogsResponse =
        ListCatalogsResponse.newBuilder()
            .addAllCatalogs(dataCatalogsService.listCatalogs(request))
            .build()

    override suspend fun listDatabases(request: ListDatabasesRequest): ListDatabasesResponse {
        val (databases, pageInfo) = dataCatalogsService.listDatabases(request)
        return ListDatabasesResponse.newBuilder()
            .addAllDatabases(databases)
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun listSchemas(request: ListSchemasRequest): ListSchemasResponse {
        val (schemas, pageInfo) = dataCatalogsService.listSchemas(request) 
        return ListSchemasResponse.newBuilder()
            .addAllSchemas(schemas)
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun listTables(request: ListTablesRequest): ListTablesResponse {
        val (tables, pageInfo) = dataCatalogsService.listTables(request)
        return ListTablesResponse.newBuilder()
            .addAllTables(tables)
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun getBlueprintPolicy(request: GetBlueprintPolicyRequest): GetBlueprintPolicyResponse {
        val dataPolicy: DataPolicy = dataCatalogsService.getBlueprintPolicy(
            request.catalogId, request.databaseId, request.schemaId, request.tableId
        )
        return GetBlueprintPolicyResponse.newBuilder()
            .setDataPolicy(dataPolicy)
            .build()
    }
}
