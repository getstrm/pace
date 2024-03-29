package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesRequest
import com.getstrm.pace.catalogs.DataCatalog
import com.getstrm.pace.domain.IntegrationClient
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.internalException
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.processing_platforms.addRuleSet
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.withPageInfo
import com.google.rpc.BadRequest
import com.google.rpc.DebugInfo
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ResourcesService(
    val catalogsService: DataCatalogsService,
    val processingPlatformsService: ProcessingPlatformsService,
    val globalTransformsService: GlobalTransformsService,
) {
    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    private val integrationClients: Map<String, IntegrationClient>
        get() = catalogsService.catalogs + processingPlatformsService.platforms

    suspend fun listResources(request: ListResourcesRequest): PagedCollection<ResourceUrn> =
        if (request.resourcePathCount == 0 && request.integrationId.isEmpty()) {
            integrationClientUrns().withPageInfo()
        } else {
            if (request.integrationId.isEmpty())
                throw BadRequestException(
                    BadRequestException.Code.INVALID_ARGUMENT,
                    BadRequest.newBuilder()
                        .addFieldViolations(
                            BadRequest.FieldViolation.newBuilder()
                                .setField("integration_id")
                                .setDescription("integration_id is required")
                                .build()
                        )
                        .build()
                )
            integrationClient(request.integrationId).listResources(request)
        }

    suspend fun getBlueprintDataPolicy(resourceUrn: ResourceUrn): DataPolicy {
        val client = resourceUrn.integrationClient()

        val blueprint =
            if (resourceUrn.hasIntegrationFqn()) {
                return client.createBlueprint(resourceUrn.integrationFqn)
            } else {
                client.getLeaf(resourceUrn).createBlueprint()
            }
        val bluePrintWithRulesets =
            try {
                addRuleSet(blueprint) { tag: String ->
                    globalTransformsService.getTransformOrNull(
                        tag,
                        GlobalTransform.TransformCase.TAG_TRANSFORM
                    )
                }
            } catch (e: Exception) {
                log.warn("could not apply global-transforms to this blueprint", e)
                throw InternalException(
                    InternalException.Code.INTERNAL,
                    DebugInfo.newBuilder()
                        .setDetail("could not apply global-transforms to this blueprint")
                        .build()
                )
            }
        return bluePrintWithRulesets
    }

    suspend fun listGroups(
        integrationId: String,
        pageParameters: PageParameters
    ): PagedCollection<Group> = integrationClient(integrationId).listGroups(pageParameters)

    suspend fun listIntegrations(clazz: KClass<*>): List<ResourceUrn> {
        return when (clazz) {
            ProcessingPlatformClient::class -> integrationClientUrns().filter { it.hasPlatform() }
            DataCatalog::class -> integrationClientUrns().filter { it.hasCatalog() }
            else -> throw internalException("Unknown integration client type ${clazz.simpleName}")
        }
    }

    /** return a list of all Integration Client urn's, as configured during startup. */
    private suspend fun integrationClientUrns() =
        processingPlatformsService.platforms.values.map {
            ResourceUrn.newBuilder().setPlatform(it.apiProcessingPlatform).build()
        } +
            catalogsService.catalogs.values.map {
                ResourceUrn.newBuilder().setCatalog(it.apiCatalog).build()
            }

    private fun ResourceUrn.integrationClient(): IntegrationClient {
        return integrationClient(
            when (integrationCase) {
                ResourceUrn.IntegrationCase.PLATFORM -> platform.id
                ResourceUrn.IntegrationCase.CATALOG -> catalog.id
                ResourceUrn.IntegrationCase.INTEGRATION_NOT_SET,
                null -> throw internalException("Integration case is not set for $this")
            }
        )
    }

    private fun integrationClient(integrationClientId: String) =
        integrationClients[integrationClientId] ?: throwIntegrationClientNotFound()

    private fun throwIntegrationClientNotFound(
        resourcePath: List<ResourceNode> = emptyList()
    ): Nothing {
        throwNotFound(
            resourcePath.joinToString { it.name },
            "integration client",
            "First resource name in the resource path should be in [${integrationClients.keys.joinToString()}]",
        )
    }
}
