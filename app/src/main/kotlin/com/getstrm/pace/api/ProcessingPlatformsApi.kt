package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetBarePolicyRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetBarePolicyResponse
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListGroupsRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListGroupsResponse
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListProcessingPlatformsRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListProcessingPlatformsResponse
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListTablesRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListTablesResponse
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ProcessingPlatformsServiceGrpcKt
import com.getstrm.pace.service.ProcessingPlatformsService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class ProcessingPlatformsApi(
    private val processingPlatformsService: ProcessingPlatformsService,
) : ProcessingPlatformsServiceGrpcKt.ProcessingPlatformsServiceCoroutineImplBase() {

    override suspend fun listProcessingPlatforms(request: ListProcessingPlatformsRequest): ListProcessingPlatformsResponse =
        ListProcessingPlatformsResponse.newBuilder().addAllProcessingPlatforms(
            processingPlatformsService.platforms.map { (id, platform) ->
                DataPolicy.ProcessingPlatform.newBuilder()
                    .setId(id)
                    .setPlatformType(platform.type)
                    .build()
            },
        ).build()

    override suspend fun listTables(request: ListTablesRequest): ListTablesResponse =
        ListTablesResponse.newBuilder().addAllTables(
            processingPlatformsService.listProcessingPlatformTables(request.platformId).map { it.fullName },
        ).build()

    override suspend fun listGroups(request: ListGroupsRequest): ListGroupsResponse =
        ListGroupsResponse.newBuilder().addAllGroups(
            processingPlatformsService.listProcessingPlatformGroups(request.platformId).map { it.name },
        ).build()

    override suspend fun getBarePolicy(request: GetBarePolicyRequest): GetBarePolicyResponse =
        GetBarePolicyResponse.newBuilder()
            .setDataPolicy(processingPlatformsService.createBarePolicy(request.platformId, request.table))
            .build()
}