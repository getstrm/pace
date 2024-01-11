package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.*
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table as ApiTable
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageResponse
import com.getstrm.pace.config.PPConfig
import com.getstrm.pace.exceptions.throwUnimplemented
import com.getstrm.pace.util.DEFAULT_PAGE_PARAMETERS
import com.getstrm.pace.util.PagedCollection
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

    /**
     * return the up- and downstream tables of a table identified by its
     * fully qualified name.
     */
    open fun getLineage(request: GetLineageRequest): GetLineageResponse {
        throwUnimplemented("Lineage in platform ${config.type}")
    }

    /**
     * create a blueprint via its fully qualified table name.
     */
    open fun createBlueprint(fqn: String): DataPolicy {
        throwUnimplemented("createBlueprint from fully qualified name in platform ${config.type}")
    }

    /** meta information database */
    abstract class Database(
        open val platformClient: ProcessingPlatformClient,
        val id: String,
        // Todo: make fields required
        val dbType: String? = null,
        val displayName: String? = null
    ) {
        abstract suspend fun listSchemas(
            pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
        ): PagedCollection<Schema>

        override fun toString() =
            dbType?.let { "Database($id, $dbType, $displayName)" } ?: "Database($id)"

        abstract suspend fun getSchema(schemaId: String): Schema

        val apiDatabase: build.buf.gen.getstrm.pace.api.entities.v1alpha.Database
            get() =
                build.buf.gen.getstrm.pace.api.entities.v1alpha.Database.newBuilder()
                    .setProcessingPlatform(platformClient.apiProcessingPlatform)
                    .setDisplayName(displayName.orEmpty())
                    .setId(id)
                    .setType(dbType.orEmpty())
                    .build()
    }

    /** A schema is a collection of tables. */
    abstract class Schema(val database: Database, val id: String, val name: String) {
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
    abstract class Table(val schema: Schema, val id: String, val name: String) {
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
