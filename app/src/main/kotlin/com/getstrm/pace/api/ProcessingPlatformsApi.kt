package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.entities.v1alpha.Database
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetBlueprintPolicyRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetBlueprintPolicyResponse
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageResponse
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListDatabasesRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListDatabasesResponse
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListGroupsRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListGroupsResponse
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListProcessingPlatformsRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListProcessingPlatformsResponse
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListSchemasRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListSchemasResponse
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListTablesRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ListTablesResponse
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.ProcessingPlatformsServiceGrpcKt
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesRequest
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.service.LineageService
import com.getstrm.pace.service.ResourcesService
import com.getstrm.pace.util.orDefault
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class ProcessingPlatformsApi(
    private val resourcesService: ResourcesService,
    private val lineageService: LineageService,
) : ProcessingPlatformsServiceGrpcKt.ProcessingPlatformsServiceCoroutineImplBase() {

    override suspend fun listProcessingPlatforms(
        request: ListProcessingPlatformsRequest
    ): ListProcessingPlatformsResponse =
        ListProcessingPlatformsResponse.newBuilder()
            .addAllProcessingPlatforms(
                resourcesService.listIntegrations(ProcessingPlatformClient::class).map {
                    it.platform
                }
            )
            .build()

    override suspend fun listDatabases(request: ListDatabasesRequest): ListDatabasesResponse {
        println(request)
        val resourcesRequest =
            ListResourcesRequest.newBuilder()
                .setUrn(
                    ResourceUrn.newBuilder()
                        .setPlatform(
                            ProcessingPlatform.newBuilder().setId(request.platformId).build()
                        )
                        .build()
                )
                .build()

        val (databases, pageInfo) =
            resourcesService.listResources(resourcesRequest).map { urn ->
                with(urn.resourcePathList[0]) {
                    Database.newBuilder().setId(name).setDisplayName(displayName).build()
                }
            }

        return ListDatabasesResponse.newBuilder()
            .addAllDatabases(databases)
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun listSchemas(request: ListSchemasRequest): ListSchemasResponse {
        val resourcesRequest =
            ListResourcesRequest.newBuilder()
                .setUrn(
                    ResourceUrn.newBuilder()
                        .setPlatform(
                            ProcessingPlatform.newBuilder().setId(request.platformId).build()
                        )
                        .addAllResourcePath(
                            listOf(ResourceNode.newBuilder().setName(request.databaseId).build())
                        )
                        .build()
                )
                .build()

        val (schemas, pageInfo) =
            resourcesService.listResources(resourcesRequest).map { urn ->
                val database =
                    urn.resourcePathList[0].let {
                        Database.newBuilder().setId(it.name).setDisplayName(it.displayName).build()
                    }

                urn.resourcePathList[1].let {
                    Schema.newBuilder()
                        .setId(it.name)
                        .setName(it.displayName)
                        .setDatabase(database)
                        .build()
                }
            }
        return ListSchemasResponse.newBuilder().addAllSchemas(schemas).setPageInfo(pageInfo).build()
    }

    override suspend fun listTables(request: ListTablesRequest): ListTablesResponse {
        val resourcesRequest =
            ListResourcesRequest.newBuilder()
                .setUrn(
                    ResourceUrn.newBuilder()
                        .setPlatform(
                            ProcessingPlatform.newBuilder().setId(request.platformId).build()
                        )
                        .addAllResourcePath(
                            listOf(
                                ResourceNode.newBuilder().setName(request.databaseId).build(),
                                ResourceNode.newBuilder().setName(request.schemaId).build()
                            )
                        )
                        .build()
                )
                .build()

        val (tables, pageInfo) =
            resourcesService.listResources(resourcesRequest).map { urn ->
                val database =
                    urn.resourcePathList[0].let {
                        Database.newBuilder().setId(it.name).setDisplayName(it.displayName).build()
                    }

                val schema =
                    urn.resourcePathList[1].let {
                        Schema.newBuilder()
                            .setId(it.name)
                            .setName(it.displayName)
                            .setDatabase(database)
                            .build()
                    }

                urn.resourcePathList[2].let {
                    Table.newBuilder()
                        .setId(it.name)
                        .setName(it.displayName)
                        .setSchema(schema)
                        .build()
                }
            }

        return ListTablesResponse.newBuilder().addAllTables(tables).setPageInfo(pageInfo).build()
    }

    override suspend fun listGroups(request: ListGroupsRequest): ListGroupsResponse {
        val (groups, pageInfo) =
            resourcesService.listGroups(request.platformId, request.pageParameters.orDefault())
        return ListGroupsResponse.newBuilder()
            .addAllGroups(groups.map { it.name })
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun getBlueprintPolicy(
        request: GetBlueprintPolicyRequest
    ): GetBlueprintPolicyResponse {
        val resourceUrn =
            ResourceUrn.newBuilder()
                .setPlatform(ProcessingPlatform.newBuilder().setId(request.platformId).build())
                .addAllResourcePath(
                    listOf(
                        ResourceNode.newBuilder().setName(request.table.schema.database.id).build(),
                        ResourceNode.newBuilder().setName(request.table.schema.id).build(),
                        ResourceNode.newBuilder().setName(request.table.id).build()
                    )
                )
                .build()

        return GetBlueprintPolicyResponse.newBuilder()
            .setDataPolicy(resourcesService.getBlueprintDataPolicy(resourceUrn))
            .build()
    }

    override suspend fun getLineage(request: GetLineageRequest): GetLineageResponse {
        return GetLineageResponse.newBuilder()
            .setLineageSummary(lineageService.getLineage(request))
            .build()
    }
}
