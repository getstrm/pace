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
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.entities.v1alpha.dataCatalog
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import com.getstrm.pace.catalogs.DataCatalog
import com.getstrm.pace.service.LegacyHierarchyService
import com.getstrm.pace.service.ResourcesService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class DataCatalogsApi(
    private val resourcesService: ResourcesService,
    private val legacyHierarchyService: LegacyHierarchyService,
) : DataCatalogsServiceGrpcKt.DataCatalogsServiceCoroutineImplBase() {

    override suspend fun listCatalogs(request: ListCatalogsRequest): ListCatalogsResponse =
        ListCatalogsResponse.newBuilder()
            .addAllCatalogs(
                resourcesService.listIntegrations(DataCatalog::class).map { it.catalog }
            )
            .build()

    override suspend fun listDatabases(request: ListDatabasesRequest): ListDatabasesResponse {
        val (databases, pageInfo) =
            legacyHierarchyService.listDatabases(
                resourceUrn { catalog = dataCatalog { id = request.catalogId } }
            )

        return ListDatabasesResponse.newBuilder()
            .addAllDatabases(databases)
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun listSchemas(request: ListSchemasRequest): ListSchemasResponse {
        val (schemas, pageInfo) =
            legacyHierarchyService.listSchemas(
                resourceUrn { catalog = dataCatalog { id = request.catalogId } },
                request.databaseId
            )
        return ListSchemasResponse.newBuilder().addAllSchemas(schemas).setPageInfo(pageInfo).build()
    }

    override suspend fun listTables(request: ListTablesRequest): ListTablesResponse {
        val (tables, pageInfo) =
            legacyHierarchyService.listTables(
                resourceUrn { catalog = dataCatalog { id = request.catalogId } },
                request.databaseId,
                request.schemaId
            )
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
