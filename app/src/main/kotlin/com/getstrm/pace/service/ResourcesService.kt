package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesRequest
import com.getstrm.pace.domain.IntegrationClient
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.withPageInfo
import org.springframework.stereotype.Component

@Component
class ResourcesService(
    val catalogsService: DataCatalogsService,
    val processingPlatformsService: ProcessingPlatformsService,
) {
    val integrationClients: List<IntegrationClient>
        get() = catalogsService.catalogs.values + processingPlatformsService.platforms.values

    suspend fun listResources(request: ListResourcesRequest): PagedCollection<ResourceUrn> {
        if (request.urn.resourcePathList.isEmpty()) {
            return (processingPlatformsService.platforms.values.map {
                    ResourceUrn.newBuilder().setPlatform(it.apiProcessingPlatform).build()
                } +
                    catalogsService.catalogs.values.map {
                        ResourceUrn.newBuilder().setCatalog(it.apiCatalog).build()
                    })
                .withPageInfo()
        }
        val client = GetResourcesClient(request.urn)
        val resources = client.listResources(request)
        return resources
    }

    private suspend fun GetResourcesClient(urn: ResourceUrn): IntegrationClient {
        val integrationClientId =
            urn.resourcePathList.firstOrNull()?.name
                ?: throwNotFound("${urn.resourcePathList}", "integration client")
        val v =
            integrationClients.find { it.id == integrationClientId }
                ?: throwNotFound(integrationClientId, "integration client")

        return v
    }
}
