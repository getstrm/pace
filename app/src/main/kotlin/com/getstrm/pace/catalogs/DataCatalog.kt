package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.DEFAULT_PAGE_PARAMETERS
import com.getstrm.pace.util.THOUSAND_RECORDS
import com.google.rpc.ResourceInfo
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog as ApiCatalog
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog.Database as ApiDatabase
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog.Schema as ApiSchema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog.Table as ApiTable

/**
 * Abstraction of the physical data concepts in a data catalog.
 */
abstract class DataCatalog(
    val config: CatalogConfiguration,
) : AutoCloseable {
    val id: String
        get() = config.id
    val type: ApiCatalog.Type
        get() = config.type
    val apiCatalog: ApiCatalog
        get() = ApiCatalog.newBuilder().setId(id).setType(config.type).build()

    abstract suspend fun listDatabases(pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS): List<Database>
    
    // TODO Should be overridden by implementations to avoid query size constraints
    // TODO make more efficient implementation that does not list all the databases
    open suspend fun getDatabase(databaseId: String): Database {
        return listDatabases(PageParameters.newBuilder().setPageSize(1000).build()).find{it.id == databaseId}?:
        throw ResourceException(
            ResourceException.Code.NOT_FOUND,
            ResourceInfo.newBuilder()
                .setResourceType("Database")
                .setResourceName(databaseId)
                .setDescription("Database $databaseId not found in catalog $id")
                .setOwner("Catalog: $id")
                .build()


        )
    }
    

    /**
     * A table is a collection of columns.
     */
    abstract class Table(val schema: Schema, val id: String, val name: String) {
        abstract suspend fun getDataPolicy(): DataPolicy?
        open suspend fun getTags(): List<String> = emptyList()
        override fun toString(): String = "Table($id, $name)"
        val apiTable: ApiTable
            get() = ApiTable.newBuilder()
                .setId(id)
                .setSchema(schema.apiSchema)
                .setName(name)
                .build()
    }

    /**
     * A schema is a collection of tables.
     */
    abstract class Schema(val database: Database, val id: String, val name: String) {
        open suspend fun listTables(pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS): List<Table> = emptyList()
        open suspend fun getTags(): List<String> = emptyList()
        override fun toString(): String = "Schema($id, $name)"
        val apiSchema: ApiSchema
            get() = ApiSchema.newBuilder()
                .setId(id)
                .setName(name)
                .setDatabase(database.apiDatabase)
                .build()
        // TODO Should be overridden by implementations to avoid query size constraints
        // TODO make more efficient implementation that does not list all the tables
        open suspend fun getTable(tableId: String): Table {
            return listTables(PageParameters.newBuilder().setPageSize(1000).build()).firstOrNull { it.id == tableId }
                ?: throw ResourceException(
                    ResourceException.Code.NOT_FOUND,
                    ResourceInfo.newBuilder()
                        .setResourceType("Table")
                        .setResourceName(tableId)
                        .setDescription("Table $tableId not found in schema $id")
                        .setOwner("Schema: $id")
                        .build()
                )
            
        }
    }

    /** meta information database */
    abstract class Database(
        open val catalog: DataCatalog,
        val id: String,
        val dbType: String? = null,
        val displayName: String? = null
    ) {
        open suspend fun listSchemas(pageParameters: PageParameters = DEFAULT_PAGE_PARAMETERS): List<Schema> = emptyList()
        open suspend fun getTags(): List<String> = emptyList()
        override fun toString() = dbType?.let { "Database($id, $dbType, $displayName)" } ?: "Database($id)"

        // TODO Should be overridden by implementations to avoid query size constraints
        // TODO make more efficient implementation that does not list all the schemas
        open suspend fun getSchema(schemaId: String): Schema {
            return listSchemas(THOUSAND_RECORDS).firstOrNull { it.id == schemaId } ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Catalog Database Schema")
                    .setResourceName(schemaId)
                    .setDescription("Schema $schemaId not found in database $id of catalog $catalog.id")
                    .setOwner("Database: $id")
                    .build()
            )
        }

        val apiDatabase: ApiDatabase
            get() = ApiDatabase.newBuilder()
                .setCatalog(catalog.apiCatalog)
                .setDisplayName(displayName.orEmpty())
                .setId(id)
                .setType(dbType.orEmpty())
                .build()
    }
}
