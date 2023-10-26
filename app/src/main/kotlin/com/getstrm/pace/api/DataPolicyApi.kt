package com.getstrm.pace.api

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicyServiceGrpcKt
import build.buf.gen.getstrm.api.data_policies.v1alpha.GetCatalogBarePolicyRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.GetCatalogBarePolicyResponse
import build.buf.gen.getstrm.api.data_policies.v1alpha.GetDataPolicyRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.GetDataPolicyResponse
import build.buf.gen.getstrm.api.data_policies.v1alpha.GetProcessingPlatformBarePolicyRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.GetProcessingPlatformBarePolicyResponse
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListCatalogsRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListCatalogsResponse
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListDataPoliciesRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListDataPoliciesResponse
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListDatabasesRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListDatabasesResponse
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformGroupsRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformGroupsResponse
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformTablesRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformTablesResponse
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformsRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformsResponse
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListSchemasRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListSchemasResponse
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListTablesRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListTablesResponse
import build.buf.gen.getstrm.api.data_policies.v1alpha.UpsertDataPolicyRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.UpsertDataPolicyResponse
import com.getstrm.pace.service.CatalogService
import com.getstrm.pace.service.DataPolicyService
import com.getstrm.pace.service.ProcessingPlatformsService
import net.devh.boot.grpc.server.service.GrpcService

// Todo: probably split in two separate services, depending on upcoming proto definition changes
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
            processingPlatformsService.listProcessingPlatformTables(request.platformId).map { it.fullName },
        ).build()

    override suspend fun listProcessingPlatformGroups(request: ListProcessingPlatformGroupsRequest): ListProcessingPlatformGroupsResponse =
        ListProcessingPlatformGroupsResponse.newBuilder().addAllGroups(
            processingPlatformsService.listProcessingPlatformGroups(request.platformId).map { it.name },
        ).build()

    override suspend fun getProcessingPlatformBarePolicy(request: GetProcessingPlatformBarePolicyRequest): GetProcessingPlatformBarePolicyResponse =
        GetProcessingPlatformBarePolicyResponse.newBuilder()
            .setDataPolicy(processingPlatformsService.createBarePolicy(request.platformId, request.table))
            .build()

    override suspend fun listCatalogs(request: ListCatalogsRequest): ListCatalogsResponse =
        ListCatalogsResponse.newBuilder()
            .addAllCatalogs(catalogService.listCatalogs())
            .build()

    override suspend fun listDatabases(request: ListDatabasesRequest): ListDatabasesResponse {
        val databases = catalogService.listDatabases(request.catalogId)
        return ListDatabasesResponse.newBuilder()
            .addAllDatabases(databases)
            .build()
    }
    override suspend fun listSchemas(request: ListSchemasRequest): ListSchemasResponse {
        val schemas = catalogService.listSchemas(request.catalogId, request.databaseId)
        return ListSchemasResponse.newBuilder()
            .addAllSchemas(schemas)
            .build()
    }
    override suspend fun listTables(request: ListTablesRequest): ListTablesResponse {
        val tables = catalogService.listTables(request.catalogId, request.databaseId, request.schemaId)
        return ListTablesResponse.newBuilder()
            .addAllTables(tables)
            .build()
    }

    override suspend fun getCatalogBarePolicy(request: GetCatalogBarePolicyRequest): GetCatalogBarePolicyResponse {
        val dataPolicy: DataPolicy = catalogService.getBarePolicy(
            request.catalogId, request.databaseId, request.schemaId, request.tableId
        )
        return GetCatalogBarePolicyResponse.newBuilder()
            .setDataPolicy(dataPolicy)
            .build()
    }
}
