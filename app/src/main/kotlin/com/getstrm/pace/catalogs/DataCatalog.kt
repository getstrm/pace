package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog as ApiCatalog
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Database as ApiDatabase
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema as ApiSchema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table as ApiTable
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.domain.IntegrationClient
import com.getstrm.pace.domain.Level1
import com.getstrm.pace.domain.Level2
import com.getstrm.pace.domain.Level3

/** Abstraction of the physical data concepts in a data catalog. */
abstract class DataCatalog(
    val config: CatalogConfiguration,
) : AutoCloseable, IntegrationClient() {
    override val id: String
        get() = config.id

    val type: ApiCatalog.Type
        get() = config.type

    val apiCatalog: ApiCatalog
        get() = ApiCatalog.newBuilder().setId(id).setType(config.type).build()

    abstract suspend fun getDatabase(databaseId: String): Level1

    /** A table is a collection of columns. */
    abstract class Table(val schema: Schema, override val id: String, val name: String) : Level3() {
        override fun toString(): String = "Table($id, $name)"

        override fun fqn(): String {
            return "${schema.fqn()}.${id}"
        }

        val apiTable: ApiTable
            get() =
                ApiTable.newBuilder().setId(id).setSchema(schema.apiSchema).setName(name).build()
    }

    /** A schema is a collection of tables. */
    abstract class Schema(val database: Database, override val id: String, val name: String) :
        Level2() {

        override fun fqn(): String {
            return "${database.id}.$id"
        }

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
        override val id: String,
        val dbType: String? = null,
        val displayName: String? = null
    ) : Level1() {

        override fun fqn(): String {
            return id
        }

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
