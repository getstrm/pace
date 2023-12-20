package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.*
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.service.DataCatalogsService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class DataCatalogsApi(private val dataCatalogsService: DataCatalogsService) :
    DataCatalogsServiceGrpcKt.DataCatalogsServiceCoroutineImplBase() {

    override suspend fun listCatalogs(request: ListCatalogsRequest): ListCatalogsResponse =
        ListCatalogsResponse.newBuilder().addAllCatalogs(dataCatalogsService.listCatalogs()).build()

    override suspend fun listDatabases(request: ListDatabasesRequest): ListDatabasesResponse {
        val databases = dataCatalogsService.listDatabases(request.catalogId)
        return ListDatabasesResponse.newBuilder().addAllDatabases(databases).build()
    }

    override suspend fun listSchemas(request: ListSchemasRequest): ListSchemasResponse {
        val schemas = dataCatalogsService.listSchemas(request.catalogId, request.databaseId)
        return ListSchemasResponse.newBuilder().addAllSchemas(schemas).build()
    }

    override suspend fun listTables(request: ListTablesRequest): ListTablesResponse {
        val tables =
            dataCatalogsService.listTables(request.catalogId, request.databaseId, request.schemaId)
        return ListTablesResponse.newBuilder().addAllTables(tables).build()
    }

    override suspend fun getBlueprintPolicy(
        request: GetBlueprintPolicyRequest
    ): GetBlueprintPolicyResponse {
        val dataPolicy: DataPolicy =
            dataCatalogsService.getBlueprintPolicy(
                request.catalogId,
                request.databaseId,
                request.schemaId,
                request.tableId
            )
        return GetBlueprintPolicyResponse.newBuilder().setDataPolicy(dataPolicy).build()
    }
}
