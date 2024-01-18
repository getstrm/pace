package com.getstrm.pace.domain

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesRequest
import com.getstrm.pace.catalogs.DataCatalog
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.DEFAULT_PAGE_PARAMETERS
import com.getstrm.pace.util.MILLION_RECORDS
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.orDefault
import com.google.rpc.BadRequest

abstract class Level1 : Resource {
    abstract suspend fun listChildren(
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Level2>

    abstract suspend fun getChild(childId: String): Level2
}

abstract class Level2 : Resource {
    abstract suspend fun listChildren(
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Level3>

    abstract suspend fun getChild(childId: String): Level3
}

abstract class Level3 : Resource {

    /**
     * create a blueprint from the field information and possible the global transforms.
     *
     * NOTE: do not call this method `get...` (Bean convention) because then the lazy information
     * gathering for creating blueprints becomes non-lazy. You can see this for instance with the
     * DataHub implementation
     */
    abstract suspend fun createBlueprint(): DataPolicy
}

// TODO make sealed, but then it has to be in the same package as the two client implemenations.
abstract class IntegrationClient {
    abstract val id: String

    open suspend fun listResources(request: ListResourcesRequest): PagedCollection<ResourceUrn> {
        return list(request.urn, request.pageParameters.orDefault())
    }

    open suspend fun platformResourceName(index: Int): String = "Level-$index"

    abstract suspend fun listDatabases(
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Level1>

    open suspend fun listSchemas(
        databaseId: String,
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Level2> =
        (listDatabases(MILLION_RECORDS).find { it.id == databaseId }
                ?: throwNotFound(databaseId, "database"))
            .listChildren(pageParameters)

    open suspend fun listTables(
        databaseId: String,
        schemaId: String,
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Level3> =
        (listSchemas(databaseId, MILLION_RECORDS).find { it.id == schemaId }
                ?: throwNotFound(schemaId, "schema"))
            .listChildren(pageParameters)

    open suspend fun getTable(databaseId: String, schemaId: String, tableId: String): Level3 =
        (listTables(databaseId, schemaId, MILLION_RECORDS).find { it.id == tableId }
            ?: throwNotFound(tableId, "table"))

    open suspend fun list(
        resourceUrn: ResourceUrn,
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<ResourceUrn> {
        val platformResourceName = platformResourceName(resourceUrn.resourcePathCount)

        return when (resourceUrn.resourcePathCount) {
            1 ->
                listDatabases(pageParameters).map {
                    it.toResourceUrn(this, resourceUrn, platformResourceName)
                }
            2 ->
                listSchemas(resourceUrn.resourcePathList[1].name, pageParameters).map {
                    it.toResourceUrn(this, resourceUrn, platformResourceName)
                }
            3 ->
                listTables(
                        resourceUrn.resourcePathList[1].name,
                        resourceUrn.resourcePathList[2].name,
                        pageParameters
                    )
                    .map { it.toResourceUrn(this, resourceUrn, platformResourceName, true) }
            else ->
                throw BadRequestException(
                    BadRequestException.Code.INVALID_ARGUMENT,
                    BadRequest.newBuilder()
                        .addAllFieldViolations(
                            listOf(
                                BadRequest.FieldViolation.newBuilder()
                                    .setField("urn.resourcePathCount")
                                    .setDescription(
                                        "Resource path count ${resourceUrn.resourcePathCount} is not supported for integration type ${resourceUrn.platform.platformType}"
                                    )
                                    .build()
                            )
                        )
                        .build()
                )
        }
    }
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
