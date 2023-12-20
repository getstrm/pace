package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.*
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.catalogs.DataCatalog
import com.getstrm.pace.config.PPConfig
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
    abstract suspend fun listGroups(pageParameters: PageParameters): PagedCollection<Group>
    abstract suspend fun applyPolicy(dataPolicy: DataPolicy)

    abstract suspend fun listDatabases(pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS): PagedCollection<DataCatalog.Database>
    abstract suspend fun getDatabase(databaseId: String): DataCatalog.Database
    
    /**
     * A table is a collection of columns.
     */
    abstract class Table(val schema: Schema, val id: String, val name: String) {
        /**
         * create a blueprint from the field information and possible the global transforms.
         *
         * NOTE: do not call this method `get...` (Bean convention) because then the lazy information
         * gathering for creating blueprints becomes non-lazy. You can see this for
         * instance with the DataHub implementation
         */
        abstract suspend fun createBlueprint(): DataPolicy?
        override fun toString(): String = "Table($id, $name)"
        val apiTable: build.buf.gen.getstrm.pace.api.entities.v1alpha.Table
            get() = build.buf.gen.getstrm.pace.api.entities.v1alpha.Table.newBuilder()
                .setId(id)
                .setSchema(schema.apiSchema)
                .setName(name)
                .build()
    }

    /**
     * A schema is a collection of tables.
     */
    abstract class Schema(val database: Database, val id: String, val name: String) {
        abstract suspend fun listTables(pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS): PagedCollection<Table>
        override fun toString(): String = "Schema($id, $name)"
        val apiSchema: build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema
            get() = build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema.newBuilder()
                .setId(id)
                .setName(name)
                .setDatabase(database.apiDatabase)
                .build()
        abstract suspend fun getTable(tableId: String): Table
    }

    /** meta information database */
    abstract class Database(
        open val catalog: DataCatalog,
        val id: String,
        val dbType: String? = null,
        val displayName: String? = null
    ) {
        abstract suspend fun listSchemas(pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS): PagedCollection<Schema>
        override fun toString() = dbType?.let { "Database($id, $dbType, $displayName)" } ?: "Database($id)"

        abstract suspend fun getSchema(schemaId: String): Schema

        val apiDatabase: build.buf.gen.getstrm.pace.api.entities.v1alpha.Database
            get() = build.buf.gen.getstrm.pace.api.entities.v1alpha.Database.newBuilder()
                .setCatalog(catalog.apiCatalog)
                .setDisplayName(displayName.orEmpty())
                .setId(id)
                .setType(dbType.orEmpty())
                .build()
    }
}

data class Group(val id: String, val name: String, val description: String? = null)

abstract class UNUSED_I_THINK {
    abstract val fullName: String
    abstract suspend fun toDataPolicy(platform: ProcessingPlatform): DataPolicy
    override fun toString(): String = "${javaClass.simpleName}(fullName=$fullName)"
    companion object {
        private val regex = """(?:pace)\:\:((?:\"[\w\s\-\_]+\"|[\w\-\_]+))""".toRegex()
        fun <T> Field<T>.toTags(): List<String> {
            val match = regex.findAll(comment)
            return match.map {
                it.groupValues[1].trim('"')
            }.toList()
        }
    }
}
