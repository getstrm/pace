package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.*
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table as ApiTable
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import com.apollographql.apollo3.mpp.platform
import com.getstrm.pace.config.PPConfig
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.throwUnimplemented
import com.getstrm.pace.util.DEFAULT_PAGE_PARAMETERS
import com.getstrm.pace.util.PagedCollection
import com.google.rpc.BadRequest
import org.jooq.Field

abstract class ProcessingPlatformClient(
    open val config: PPConfig,
) {
    val id: String
        get() = config.id

    val type: ProcessingPlatform.PlatformType
        get() = config.type

    val apiProcessingPlatform: ProcessingPlatform
        get() = ProcessingPlatform.newBuilder().setId(id).setPlatformType(config.type).build()

    abstract suspend fun listGroups(pageParameters: PageParameters): PagedCollection<Group>

    abstract suspend fun applyPolicy(dataPolicy: DataPolicy)

    open suspend fun list(
        resourceUrn: ResourceUrn,
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<ResourceUrn> {
        val platformResourceName = platformResourceName(resourceUrn.resourcePathCount)

        return when (resourceUrn.resourcePathCount) {
            0 ->
                listDatabases(pageParameters).map {
                    it.toResourceUrn(resourceUrn, platformResourceName)
                }
            1 ->
                listSchemas(resourceUrn.resourcePathList[0].name, pageParameters).map {
                    it.toResourceUrn(resourceUrn, platformResourceName)
                }
            2 ->
                listTables(
                        resourceUrn.resourcePathList[0].name,
                        resourceUrn.resourcePathList[1].name,
                        pageParameters
                    )
                    .map { it.toResourceUrn(resourceUrn, platformResourceName, true) }
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

    abstract suspend fun platformResourceName(index: Int): String

    abstract suspend fun listDatabases(
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Database>

    abstract suspend fun listSchemas(
        databaseId: String,
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Schema>

    abstract suspend fun listTables(
        databaseId: String,
        schemaId: String,
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Table>

    abstract suspend fun getTable(databaseId: String, schemaId: String, tableId: String): Table

    /** return the up- and downstream tables of a table identified by its fully qualified name. */
    open suspend fun getLineage(request: GetLineageRequest): LineageSummary {
        throwUnimplemented("Lineage in platform ${config.type}")
    }

    /** create a blueprint via its fully qualified table name. */
    open fun createBlueprint(fqn: String): DataPolicy {
        throwUnimplemented("createBlueprint from fully qualified name in platform ${config.type}")
    }

    interface Resource {
        val id: String

        fun toResourceUrn(
            parentResourceUrn: ResourceUrn,
            platformName: String,
            isLeafNode: Boolean = false
        ): ResourceUrn =
            ResourceUrn.newBuilder()
                .setPlatform(parentResourceUrn.platform)
                .addAllResourcePath(
                    parentResourceUrn.resourcePathList +
                        listOf(
                            ResourceNode.newBuilder()
                                .setName(id)
                                .setPlatformName(platformName)
                                .setIsLeaf(isLeafNode)
                                .build()
                        )
                )
                .build()
    }

    /** meta information database */
    abstract class Database(
        open val platformClient: ProcessingPlatformClient,
        override val id: String,
        val dbType: ProcessingPlatform.PlatformType,
        val displayName: String? = id
    ) : Resource {
        abstract suspend fun listSchemas(
            pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
        ): PagedCollection<Schema>

        override fun toString() = "Database($id, $dbType, $displayName)"

        abstract suspend fun getSchema(schemaId: String): Schema

        val apiDatabase: build.buf.gen.getstrm.pace.api.entities.v1alpha.Database
            get() =
                build.buf.gen.getstrm.pace.api.entities.v1alpha.Database.newBuilder()
                    .setProcessingPlatform(platformClient.apiProcessingPlatform)
                    .setDisplayName(displayName.orEmpty())
                    .setId(id)
                    .setType(dbType.name)
                    .build()
    }

    /** A schema is a collection of tables. */
    abstract class Schema(val database: Database, override val id: String, val name: String) :
        Resource {
        abstract suspend fun listTables(
            pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
        ): PagedCollection<Table>

        override fun toString(): String = "Schema($id, $name)"

        val apiSchema: build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema
            get() =
                build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema.newBuilder()
                    .setId(id)
                    .setName(name)
                    .setDatabase(database.apiDatabase)
                    .build()

        abstract suspend fun getTable(tableId: String): Table
    }

    companion object {
        private val regex = """(?:pace)\:\:((?:\"[\w\s\-\_]+\"|[\w\-\_]+))""".toRegex()

        fun <T> Field<T>.toTags(): List<String> {
            val match = regex.findAll(comment)
            return match.map { it.groupValues[1].trim('"') }.toList()
        }
    }

    /** A table is a collection of columns. */
    abstract class Table(val schema: Schema, override val id: String, val name: String) : Resource {
        /**
         * create a blueprint from the field information and possible the global transforms.
         *
         * NOTE: do not call this method `get...` (Bean convention) because then the lazy
         * information gathering for creating blueprints becomes non-lazy. You can see this for
         * instance with the DataHub implementation
         */
        abstract suspend fun createBlueprint(): DataPolicy

        override fun toString(): String = "${schema.database.id}.${schema.id}.$id"

        /** the full name to be used in SQL queries to get at the source data. */
        abstract val fullName: String
        val apiPlatform = schema.database.platformClient.apiProcessingPlatform
        val apiTable: ApiTable
            get() =
                ApiTable.newBuilder().setId(id).setSchema(schema.apiSchema).setName(name).build()
    }
}

data class Group(val id: String, val name: String, val description: String? = null)
