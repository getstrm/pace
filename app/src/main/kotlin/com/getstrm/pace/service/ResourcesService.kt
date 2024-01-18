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
    private val integrationClients: List<IntegrationClient>
        get() = catalogsService.catalogs.values + processingPlatformsService.platforms.values

    suspend fun listResources(request: ListResourcesRequest): PagedCollection<ResourceUrn> =
        if (request.urn.resourcePathList.isEmpty()) {
            integrationClientUrns().withPageInfo()
        } else {
            integrationClient(request.urn).listResources(request)
        }

    /** return a list of all Integration Client urn's, as configured during startup. */
    private suspend fun integrationClientUrns() =
        processingPlatformsService.platforms.values.map {
            ResourceUrn.newBuilder().setPlatform(it.apiProcessingPlatform).build()
        } +
            catalogsService.catalogs.values.map {
                ResourceUrn.newBuilder().setCatalog(it.apiCatalog).build()
            }

    /** find the integration client responding for handling a certain urn. */
    private suspend fun integrationClient(urn: ResourceUrn): IntegrationClient {
        val integrationClientId =
            urn.resourcePathList.firstOrNull()?.name
                ?: throwNotFound(urn.resourcePathList.joinToString(), "integration client")

        return integrationClients.find { it.id == integrationClientId }
            ?: throwNotFound(integrationClientId, "integration client")
    }
}
