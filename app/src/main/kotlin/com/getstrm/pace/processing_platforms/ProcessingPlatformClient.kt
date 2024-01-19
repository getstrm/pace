package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.*
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table as ApiTable
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import com.getstrm.pace.config.PPConfig
import com.getstrm.pace.domain.IntegrationClient
import com.getstrm.pace.domain.LeafResource
import com.getstrm.pace.domain.Resource
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.exceptions.throwUnimplemented
import com.getstrm.pace.util.MILLION_RECORDS
import com.getstrm.pace.util.PagedCollection
import org.jooq.Field

abstract class ProcessingPlatformClient(open val config: PPConfig) : IntegrationClient() {
    override val id
        get() = config.id

    val type: ProcessingPlatform.PlatformType
        get() = config.type

    val apiProcessingPlatform: ProcessingPlatform
        get() = ProcessingPlatform.newBuilder().setId(id).setPlatformType(config.type).build()

    override suspend fun getChild(childId: String): Resource =
        listDatabases(MILLION_RECORDS).find { it.id == childId }
            ?: throwNotFound(childId, "database")

    override suspend fun listChildren(pageParameters: PageParameters): PagedCollection<Resource> =
        listDatabases(pageParameters)

    abstract suspend fun applyPolicy(dataPolicy: DataPolicy)

    /** return the up- and downstream tables of a table identified by its fully qualified name. */
    open suspend fun getLineage(request: GetLineageRequest): LineageSummary {
        throwUnimplemented("Lineage in platform ${config.type}")
    }

    /** create a blueprint via its fully qualified table name. */
    open fun createBlueprint(fqn: String): DataPolicy {
        throwUnimplemented("createBlueprint from fully qualified name in platform ${config.type}")
    }

    /** meta information database */
    abstract class Database(
        open val platformClient: ProcessingPlatformClient,
        override val id: String,
        val dbType: ProcessingPlatform.PlatformType,
        override val displayName: String = id
    ) : Resource {

        override fun toString() = "Database($id, $dbType, $displayName)"

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
    abstract class Schema(
        val database: Database,
        override val id: String,
        override val displayName: String
    ) : Resource {
        override fun toString(): String = "Schema($id, $displayName)"

        val apiSchema: build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema
            get() =
                build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema.newBuilder()
                    .setId(id)
                    .setName(displayName)
                    .setDatabase(database.apiDatabase)
                    .build()
    }

    /** A table is a collection of columns. */
    abstract class Table(
        val schema: Schema,
        override val id: String,
        override val displayName: String
    ) : LeafResource() {
        override fun toString(): String = "${schema.database.id}.${schema.id}.$id"

        /** the full name to be used in SQL queries to get at the source data. */
        abstract val fullName: String
        val apiTable: ApiTable
            get() =
                ApiTable.newBuilder()
                    .setId(id)
                    .setSchema(schema.apiSchema)
                    .setName(displayName)
                    .build()
    }

    companion object {
        private val regex = """(?:pace)\:\:((?:\"[\w\s\-\_]+\"|[\w\-\_]+))""".toRegex()

        /** Used to create Pace tags from a SQL Comment on a jooq Field. */
        fun <T> Field<T>.toTags(): List<String> {
            val match = regex.findAll(comment)
            return match.map { it.groupValues[1].trim('"') }.toList()
        }
    }
}

data class Group(val id: String, val name: String, val description: String? = null)
