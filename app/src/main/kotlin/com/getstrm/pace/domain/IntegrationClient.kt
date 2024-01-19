package com.getstrm.pace.domain

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesRequest
import com.getstrm.pace.catalogs.DataCatalog
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.exceptions.throwUnimplemented
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.DEFAULT_PAGE_PARAMETERS
import com.getstrm.pace.util.MILLION_RECORDS
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.orDefault
import com.google.rpc.BadRequest

/**
 * TODO make sealed, but then it has to be in the same package as both the
 * [ProcessingPlatformClient] and [DataCatalog] class.
 */
abstract class IntegrationClient : Resource {
    abstract override val id: String

    override fun fqn(): String = id

    override val displayName: String
        get() = id

    open suspend fun listResources(request: ListResourcesRequest): PagedCollection<ResourceUrn> {
        val platformResourceName = platformResourceName(request.urn.resourcePathCount)
        return getResource(request.urn).listChildren(request.pageParameters.orDefault()).map {
            it.toResourceUrn(this, request.urn, platformResourceName)
        }
    }

    private suspend fun getResource(urn: ResourceUrn): Resource =
        urn.resourcePathList.fold(this as Resource) { parent, node -> parent.getChild(node.name) }

    open suspend fun listGroups(pageParameters: PageParameters): PagedCollection<Group> =
        throwUnimplemented(
            "List groups is not implemented for integration $id of type ${this::class.simpleName}"
        )

    suspend fun getLeaf(leafResourceUrn: ResourceUrn): LeafResource {
        val resource = getResource(leafResourceUrn)

        if (resource !is LeafResource)
            throw BadRequestException(
                BadRequestException.Code.INVALID_ARGUMENT,
                BadRequest.newBuilder()
                    .addAllFieldViolations(
                        listOf(
                            BadRequest.FieldViolation.newBuilder()
                                .setField("urn")
                                .setDescription("The urn does not point to a leaf resource")
                                .build(),
                        )
                    )
                    .build()
            )

        return resource
    }

    open suspend fun platformResourceName(index: Int): String = "Level-$index"

    abstract suspend fun listDatabases(
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Resource>

    open suspend fun listSchemas(
        databaseId: String,
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Resource> =
        (listDatabases(MILLION_RECORDS).find { it.id == databaseId }
                ?: throwNotFound(databaseId, "database"))
            .listChildren(pageParameters)

    open suspend fun listTables(
        databaseId: String,
        schemaId: String,
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Resource> =
        (listSchemas(databaseId, MILLION_RECORDS).find { it.id == schemaId }
                ?: throwNotFound(schemaId, "schema"))
            .listChildren(pageParameters)
            .map { it as LeafResource }

    open suspend fun getTable(databaseId: String, schemaId: String, tableId: String): Resource =
        (listTables(databaseId, schemaId, MILLION_RECORDS).find { it.id == tableId }
            ?: throwNotFound(tableId, "table"))
}

interface Resource {
    // TODO should become name!
    val id: String

    val displayName: String

    fun fqn(): String

    suspend fun listChildren(
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Resource>

    // FIXME childId is the resourceNode.name, we should rename this
    suspend fun getChild(childId: String): Resource

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
                                .setDisplayName(displayName)
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

abstract class LeafResource : Resource {

    override suspend fun getChild(childId: String): Resource {
        throwNotFound(childId, "$id has no children")
    }

    override suspend fun listChildren(pageParameters: PageParameters): PagedCollection<Resource> {
        throwNotFound(id, "has no children")
    }
    /**
     * create a blueprint from the field information and possible the global transforms.
     *
     * NOTE: do not call this method `get...` (Bean convention) because then the lazy information
     * gathering for creating blueprints becomes non-lazy. You can see this for instance with the
     * DataHub implementation
     */
    abstract suspend fun createBlueprint(): DataPolicy
}
