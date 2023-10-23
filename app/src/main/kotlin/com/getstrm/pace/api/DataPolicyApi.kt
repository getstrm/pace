package com.getstrm.pace.api

import com.getstrm.pace.service.ProcessingPlatformsService
import build.buf.gen.getstrm.api.data_policies.v1alpha.*
import com.getstrm.pace.service.DataPolicyService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class DataPolicyApi(
    private val dataPolicyService: DataPolicyService,
    private val processingPlatformsService: ProcessingPlatformsService,
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
            .setDataPolicy(dataPolicyService.getLatestDataPolicy(request.id))
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
            .setDataPolicy(processingPlatformsService.createBarePolicy(request.platform, request.table))
            .build()
}
