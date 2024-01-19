package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog as ApiCatalog
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Database as ApiDatabase
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema as ApiSchema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table as ApiTable
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.domain.IntegrationClient
import com.getstrm.pace.domain.LeafResource
import com.getstrm.pace.domain.Resource
import com.getstrm.pace.util.PagedCollection

/** Abstraction of the physical data concepts in a data catalog. */
abstract class DataCatalog(val config: CatalogConfiguration) : AutoCloseable, IntegrationClient() {
    override val id: String
        get() = config.id

    val type: ApiCatalog.Type
        get() = config.type

    val apiCatalog: ApiCatalog
        get() = ApiCatalog.newBuilder().setId(id).setType(config.type).build()

    override suspend fun getChild(childId: String): Resource = getDatabase(childId)

    override suspend fun listChildren(pageParameters: PageParameters): PagedCollection<Resource> =
        listDatabases(pageParameters)

    abstract suspend fun getDatabase(databaseId: String): Resource

    /** A table is a collection of columns. */
    abstract class Table(val schema: Schema, override val id: String, val name: String) :
        LeafResource() {
        override fun toString(): String = "Table($id, $name)"

        override fun fqn(): String = "${schema.fqn()}.${id}"

        val apiTable: ApiTable
            get() =
                ApiTable.newBuilder().setId(id).setSchema(schema.apiSchema).setName(name).build()
    }

    /** A schema is a collection of tables. */
    abstract class Schema(val database: Database, override val id: String, val name: String) :
        Resource {

        override fun fqn(): String = "${database.id}.$id"

        override fun toString(): String = "Schema($id, $name)"

        val apiSchema: ApiSchema
            get() =
                ApiSchema.newBuilder()
                    .setId(id)
                    .setName(name)
                    .setDatabase(database.apiDatabase)
                    .build()
    }

    abstract class Database(
        open val catalog: DataCatalog,
        override val id: String,
        val dbType: String? = null,
        val displayName: String? = null
    ) : Resource {

        override fun fqn(): String = id

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
