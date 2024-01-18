package com.getstrm.pace.domain

import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesRequest
import com.getstrm.pace.catalogs.DataCatalog
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.PagedCollection

// TODO make sealed, but then it has to be in the same package as the two client implemenations.
interface IntegrationClient {
    suspend fun listResources(request: ListResourcesRequest): PagedCollection<ResourceUrn>

    val id: String
}

interface Resource {
    val id: String

    fun fqn(): String

    fun toResourceUrn(
        integrationClient: IntegrationClient,
        parentResourceUrn: ResourceUrn,
        platformName: String,
        isLeafNode: Boolean = false
    ): ResourceUrn {
        val builder =
            ResourceUrn.newBuilder()
                .setPlatformFqn(fqn())
                .addAllResourcePath(
                    parentResourceUrn.resourcePathList +
                        listOf(
                            ResourceNode.newBuilder()
                                .setName(id)
                                .setPlatformName(platformName)
                                .setIsLeaf(isLeafNode)
                                .build(),
                        ),
                )
        when (integrationClient) {
            is ProcessingPlatformClient ->
                builder.setPlatform(integrationClient.apiProcessingPlatform)
            is DataCatalog -> builder.setCatalog(integrationClient.apiCatalog)
            else -> {}
        }
        return builder.build()
    }
}
