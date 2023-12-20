package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.*
import com.getstrm.pace.service.ProcessingPlatformsService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class ProcessingPlatformsApi(
    private val processingPlatformsService: ProcessingPlatformsService,
) : ProcessingPlatformsServiceGrpcKt.ProcessingPlatformsServiceCoroutineImplBase() {

    override suspend fun listProcessingPlatforms(
        request: ListProcessingPlatformsRequest
    ): ListProcessingPlatformsResponse =
        ListProcessingPlatformsResponse.newBuilder()
            .addAllProcessingPlatforms(
                processingPlatformsService.platforms.map { (id, platform) ->
                    DataPolicy.ProcessingPlatform.newBuilder()
                        .setId(id)
                        .setPlatformType(platform.type)
                        .build()
                },
            )
            .build()

    override suspend fun listTables(request: ListTablesRequest): ListTablesResponse {
        val (tables, pageInfo) = processingPlatformsService.listProcessingPlatformTables(request)
        return ListTablesResponse.newBuilder()
            .addAllTables(tables.map { it.fullName })
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun listGroups(request: ListGroupsRequest): ListGroupsResponse {
        val (groups, pageInfo) = processingPlatformsService.listProcessingPlatformGroups(request)
        return ListGroupsResponse.newBuilder()
            .addAllGroups(groups.map { it.name })
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun getBlueprintPolicy(request: GetBlueprintPolicyRequest) =
        processingPlatformsService.getBlueprintPolicy(request.platformId, request.tableId)
}
