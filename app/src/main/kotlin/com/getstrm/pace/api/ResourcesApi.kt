package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesRequest
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesResponse
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ResourcesServiceGrpcKt
import com.getstrm.pace.service.DataCatalogsService
import com.getstrm.pace.service.ProcessingPlatformsService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class ResourcesApi(
    private val processingPlatformsService: ProcessingPlatformsService,
    private val catalogsService: DataCatalogsService,
) : ResourcesServiceGrpcKt.ResourcesServiceCoroutineImplBase() {
    override suspend fun listResources(request: ListResourcesRequest): ListResourcesResponse {
        val resources =
            when (request.urn.integrationCase) {
                ResourceUrn.IntegrationCase.PLATFORM ->
                    processingPlatformsService.listResources(request)
                ResourceUrn.IntegrationCase.CATALOG -> TODO()
                ResourceUrn.IntegrationCase.INTEGRATION_NOT_SET,
                null -> TODO()
            }

        return ListResourcesResponse.newBuilder()
            .addAllResources(resources.data)
            .setPageInfo(resources.pageInfo)
            .build()
    }
}
