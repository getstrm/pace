package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog as ApiCatalog
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Database as ApiDatabase
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema as ApiSchema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table as ApiTable
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import build.buf.gen.getstrm.pace.api.resources.v1alpha.ListResourcesRequest
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.domain.IntegrationClient
import com.getstrm.pace.util.DEFAULT_PAGE_PARAMETERS
import com.getstrm.pace.util.PagedCollection

/** Abstraction of the physical data concepts in a data catalog. */
abstract class DataCatalog(
    val config: CatalogConfiguration,
) : AutoCloseable, IntegrationClient {
    override val id: String
        get() = config.id

    val type: ApiCatalog.Type
        get() = config.type

    val apiCatalog: ApiCatalog
        get() = ApiCatalog.newBuilder().setId(id).setType(config.type).build()

    override suspend fun listResources(
        request: ListResourcesRequest
    ): PagedCollection<ResourceUrn> {
        TODO("Not yet implemented")
    }

    abstract suspend fun listDatabases(
        pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
    ): PagedCollection<Database>

    abstract suspend fun getDatabase(databaseId: String): Database

    /** A table is a collection of columns. */
    abstract class Table(val schema: Schema, val id: String, val name: String) {
        /**
         * create a blueprint from the field information and possible the global transforms.
         *
         * NOTE: do not call this method `get...` (Bean convention) because then the lazy
         * information gathering for creating blueprints becomes non-lazy. You can see this for
         * instance with the DataHub implementation
         */
        abstract suspend fun createBlueprint(): DataPolicy?

        override fun toString(): String = "Table($id, $name)"

        val apiTable: ApiTable
            get() =
                ApiTable.newBuilder().setId(id).setSchema(schema.apiSchema).setName(name).build()
    }

    /** A schema is a collection of tables. */
    abstract class Schema(val database: Database, val id: String, val name: String) {
        abstract suspend fun listTables(
            pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
        ): PagedCollection<Table>

        override fun toString(): String = "Schema($id, $name)"

        val apiSchema: ApiSchema
            get() =
                ApiSchema.newBuilder()
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
        abstract suspend fun listSchemas(
            pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS
        ): PagedCollection<Schema>

        override fun toString() =
            dbType?.let { "Database($id, $dbType, $displayName)" } ?: "Database($id)"

        abstract suspend fun getSchema(schemaId: String): Schema

        val apiDatabase: ApiDatabase
            get() =
                ApiDatabase.newBuilder()
                    .setCatalog(catalog.apiCatalog)
                    .setDisplayName(displayName.orEmpty())
                    .setId(id)
                    .setType(dbType.orEmpty())
                    .build()
    }
}
