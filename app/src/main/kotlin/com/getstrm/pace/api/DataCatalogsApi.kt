package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.DataCatalogsServiceGrpcKt
import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.GetBlueprintPolicyRequest
import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.GetBlueprintPolicyResponse
import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.ListCatalogsRequest
import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.ListCatalogsResponse
import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.ListDatabasesRequest
import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.ListDatabasesResponse
import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.ListSchemasRequest
import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.ListSchemasResponse
import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.ListTablesRequest
import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.ListTablesResponse
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog as ApiDataCatalog
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Database
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesRequest
import com.getstrm.pace.catalogs.DataCatalog
import com.getstrm.pace.service.ResourcesService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class DataCatalogsApi(private val resourcesService: ResourcesService) :
    DataCatalogsServiceGrpcKt.DataCatalogsServiceCoroutineImplBase() {

    override suspend fun listCatalogs(request: ListCatalogsRequest): ListCatalogsResponse =
        ListCatalogsResponse.newBuilder()
            .addAllCatalogs(
                resourcesService.listIntegrations(DataCatalog::class).map { it.catalog }
            )
            .build()

    override suspend fun listDatabases(request: ListDatabasesRequest): ListDatabasesResponse {
        val resourcesRequest =
            ListResourcesRequest.newBuilder()
                .setUrn(
                    ResourceUrn.newBuilder()
                        .setCatalog(ApiDataCatalog.newBuilder().setId(request.catalogId).build())
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
                        .setCatalog(ApiDataCatalog.newBuilder().setId(request.catalogId).build())
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
                        .setCatalog(ApiDataCatalog.newBuilder().setId(request.catalogId).build())
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

    override suspend fun getBlueprintPolicy(
        request: GetBlueprintPolicyRequest
    ): GetBlueprintPolicyResponse {
        val resourceUrn =
            ResourceUrn.newBuilder()
                .setCatalog(ApiDataCatalog.newBuilder().setId(request.catalogId).build())
                .addAllResourcePath(
                    listOf(
                        ResourceNode.newBuilder().setName(request.databaseId).build(),
                        ResourceNode.newBuilder().setName(request.schemaId).build(),
                        ResourceNode.newBuilder().setName(request.tableId).build()
                    )
                )
                .build()

        return GetBlueprintPolicyResponse.newBuilder()
            .setDataPolicy(resourcesService.getBlueprintDataPolicy(resourceUrn))
            .build()
    }
}
