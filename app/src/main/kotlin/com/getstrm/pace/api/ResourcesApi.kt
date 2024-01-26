package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesRequest
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesResponse
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ResourcesServiceGrpcKt
import com.getstrm.pace.service.ResourcesService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class ResourcesApi(
    private val resourcesService: ResourcesService,
) : ResourcesServiceGrpcKt.ResourcesServiceCoroutineImplBase() {
    override suspend fun listResources(request: ListResourcesRequest): ListResourcesResponse {
        val resources = resourcesService.listResources(request)
        return ListResourcesResponse.newBuilder()
            .addAllResources(resources.data)
            .setPageInfo(resources.pageInfo)
            .build()
    }
}
