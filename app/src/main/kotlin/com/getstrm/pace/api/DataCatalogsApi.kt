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

    override suspend fun listDatabases(request: ListDatabasesRequest): ListDatabasesResponse =
        ListDatabasesResponse.newBuilder()
            .addAllDatabases(dataCatalogsService.listDatabases(request))
            .build()

    override suspend fun listSchemas(request: ListSchemasRequest): ListSchemasResponse =
        ListSchemasResponse.newBuilder()
            .addAllSchemas(dataCatalogsService.listSchemas(request))
            .build()

    override suspend fun listTables(request: ListTablesRequest): ListTablesResponse = ListTablesResponse.newBuilder()
        .addAllTables(dataCatalogsService.listTables(request))
        .build()

    override suspend fun getBlueprintPolicy(request: GetBlueprintPolicyRequest): GetBlueprintPolicyResponse {
        val dataPolicy: DataPolicy = dataCatalogsService.getBlueprintPolicy(
            request.catalogId, request.databaseId, request.schemaId, request.tableId
        )
        return GetBlueprintPolicyResponse.newBuilder()
            .setDataPolicy(dataPolicy)
            .build()
    }
}
