package com.getstrm.pace.api

import build.buf.gen.getstrm.api.data_policies.v1alpha.*
import com.getstrm.pace.service.CatalogService
import com.getstrm.pace.service.DataPolicyService
import com.getstrm.pace.service.ProcessingPlatformsService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class DataPolicyApi(
    private val dataPolicyService: DataPolicyService,
    private val processingPlatformsService: ProcessingPlatformsService,
    private val catalogService: CatalogService,
) : DataPolicyServiceGrpcKt.DataPolicyServiceCoroutineImplBase() {

    override suspend fun listDataPolicies(request: ListDataPoliciesRequest): ListDataPoliciesResponse {
        return ListDataPoliciesResponse.newBuilder()
            .addAllDataPolicies(dataPolicyService.listDataPolicies())
            .build()
    }

    override suspend fun upsertDataPolicy(request: UpsertDataPolicyRequest): UpsertDataPolicyResponse {
        return UpsertDataPolicyResponse.newBuilder()
            .setDataPolicy(dataPolicyService.upsertDataPolicy(request.dataPolicy))
            .build()
    }

    override suspend fun getDataPolicy(request: GetDataPolicyRequest): GetDataPolicyResponse {
        return GetDataPolicyResponse.newBuilder()
            .setDataPolicy(dataPolicyService.getLatestDataPolicy(request.dataPolicyId))
            .build()
    }

    override suspend fun listProcessingPlatforms(request: ListProcessingPlatformsRequest): ListProcessingPlatformsResponse =
        ListProcessingPlatformsResponse.newBuilder().addAllProcessingPlatforms(
            processingPlatformsService.platforms.map { (id, platform) ->
                DataPolicy.ProcessingPlatform.newBuilder()
                    .setId(id)
                    .setPlatformType(platform.type)
                    .build()
            },
        ).build()

    override suspend fun listProcessingPlatformTables(request: ListProcessingPlatformTablesRequest): ListProcessingPlatformTablesResponse =
        ListProcessingPlatformTablesResponse.newBuilder().addAllTables(
            processingPlatformsService.listProcessingPlatformTables(request).map { it.fullName },
        ).build()

    override suspend fun listProcessingPlatformGroups(request: ListProcessingPlatformGroupsRequest): ListProcessingPlatformGroupsResponse =
        ListProcessingPlatformGroupsResponse.newBuilder().addAllGroups(
            processingPlatformsService.listProcessingPlatformGroups(request).map { it.name },
        ).build()

    override suspend fun getProcessingPlatformBarePolicy(request: GetProcessingPlatformBarePolicyRequest): GetProcessingPlatformBarePolicyResponse =
        GetProcessingPlatformBarePolicyResponse.newBuilder()
            .setDataPolicy(processingPlatformsService.createBarePolicy(request.platformId, request.tableId))
            .build()

    override suspend fun listCatalogs(request: ListCatalogsRequest): ListCatalogsResponse =
        ListCatalogsResponse.newBuilder()
            .addAllCatalogs(catalogService.listCatalogs())
            .build()

    override suspend fun listDatabases(request: ListDatabasesRequest): ListDatabasesResponse {
        val databases = catalogService.listDatabases(request.catalog)
        return ListDatabasesResponse.newBuilder()
            .addAllDatabases(databases)
            .build()
    }
    override suspend fun listSchemas(request: ListSchemasRequest): ListSchemasResponse {
        val schemas = catalogService.listSchemas(request.database)
        return ListSchemasResponse.newBuilder()
            .addAllSchemas(schemas)
            .build()
    }
    override suspend fun listTables(request: ListTablesRequest): ListTablesResponse {
        val tables = catalogService.listTables(request.schema)
        return ListTablesResponse.newBuilder()
            .addAllTables(tables)
            .build()
    }

    override suspend fun getCatalogBarePolicy(request: GetCatalogBarePolicyRequest): GetCatalogBarePolicyResponse {
        val dataPolicy: DataPolicy = catalogService.getBarePolicy(request.table)
        return GetCatalogBarePolicyResponse.newBuilder()
            .setDataPolicy(dataPolicy)
            .build()
    }
}
