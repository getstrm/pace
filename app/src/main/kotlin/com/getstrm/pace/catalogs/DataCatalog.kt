package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog as ApiCatalog
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog.Database as ApiDatabase
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog.Schema as ApiSchema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog.Table as ApiTable
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.config.CatalogConfiguration

/** Abstraction of the physical data concepts in a data catalog. */
abstract class DataCatalog(
    val config: CatalogConfiguration,
) : AutoCloseable {
    val id: String
        get() = config.id

    val type: ApiCatalog.Type
        get() = config.type

    val apiCatalog: ApiCatalog
        get() = ApiCatalog.newBuilder().setId(id).setType(config.type).build()

    abstract suspend fun listDatabases(): List<Database>

    /** A table is a collection of columns. */
    abstract class Table(val schema: Schema, val id: String, val name: String) {
        abstract suspend fun getDataPolicy(): DataPolicy?

        open suspend fun getTags(): List<String> = emptyList()

        override fun toString(): String = "Table($id, $name)"

        val apiTable: ApiTable
            get() =
                ApiTable.newBuilder().setId(id).setSchema(schema.apiSchema).setName(name).build()
    }

    /** A schema is a collection of tables. */
    abstract class Schema(val database: Database, val id: String, val name: String) {
        open suspend fun getTables(): List<Table> = emptyList()

        open suspend fun getTags(): List<String> = emptyList()

        override fun toString(): String = "Schema($id, $name)"

        val apiSchema: ApiSchema
            get() =
                ApiSchema.newBuilder()
                    .setId(id)
                    .setName(name)
                    .setDatabase(database.apiDatabase)
                    .build()
    }

    /** meta information database */
    abstract class Database(
        open val catalog: DataCatalog,
        val id: String,
        val dbType: String? = null,
        val displayName: String? = null
    ) {
        open suspend fun getSchemas(): List<Schema> = emptyList()

        open suspend fun getTags(): List<String> = emptyList()

        override fun toString() =
            dbType?.let { "Database($id, $dbType, $displayName)" } ?: "Database($id)"

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
