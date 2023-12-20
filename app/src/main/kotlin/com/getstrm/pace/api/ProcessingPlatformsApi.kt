package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.*
import com.getstrm.pace.service.ProcessingPlatformsService
import net.devh.boot.grpc.server.service.GrpcService
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Database as ApiDatabase
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema as ApiSchema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table as ApiTable

@GrpcService
class ProcessingPlatformsApi(
    private val processingPlatformsService: ProcessingPlatformsService,
) : ProcessingPlatformsServiceGrpcKt.ProcessingPlatformsServiceCoroutineImplBase() {

    override suspend fun listProcessingPlatforms(request: ListProcessingPlatformsRequest): ListProcessingPlatformsResponse =
        ListProcessingPlatformsResponse.newBuilder().addAllProcessingPlatforms(
            processingPlatformsService.platforms.map { (id, platform) ->
                ProcessingPlatform.newBuilder()
                    .setId(id)
                    .setPlatformType(platform.type)
                    .build()
            },
        ).build()

    override suspend fun listDatabases(request: ListDatabasesRequest): ListDatabasesResponse {
        // TODO just to get the cli started.
        return ListDatabasesResponse.newBuilder()
            .addDatabases(ApiDatabase.newBuilder()
                .setId("Bart")
                .setDisplayName("BBB")
            )
        .build()
    }

    override suspend fun listSchemas(request: ListSchemasRequest): ListSchemasResponse {
        // TODO just to get the cli started.
        return ListSchemasResponse.newBuilder()
            .addSchemas(
                ApiSchema.newBuilder()
                .setId("Bart ze schema")
            )
        .build()
    }
    override suspend fun listTables(request: ListTablesRequest): ListTablesResponse {
        val (tables, pageInfo) = processingPlatformsService.listProcessingPlatformTables(request)
        return ListTablesResponse.newBuilder()
            .addAllTables( tables.map {
                ApiTable.newBuilder()
                    .setId(it.fullName)
                    .setName(it.fullName)
                    .build()
                
            } )
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun listGroups(request: ListGroupsRequest): ListGroupsResponse {
        val (groups, pageInfo) = processingPlatformsService.listProcessingPlatformGroups(request)
        return ListGroupsResponse.newBuilder()
            .addAllGroups( groups.map { it.name } )
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun getBlueprintPolicy(request: GetBlueprintPolicyRequest) =
        processingPlatformsService.getBlueprintPolicy(request.platformId, request.tableId)
}
