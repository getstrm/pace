package com.getstrm.daps.config

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy


/**
 * Abstraction of the physical data concepts in a data catalog.
 */
abstract class DataCatalog: AutoCloseable {
    abstract suspend fun listDatabases(): List<Database>

    /**
     * A table is a collection of columns.
     */
    abstract class Table(val schema: Schema, val id: String, val name: String) {
        abstract suspend fun getDataPolicy(): DataPolicy?
        override fun toString(): String = "Table($id, $name)"
    }

    /**
     * A schema is a collection of tables.
     */
    abstract class Schema(val database: Database, val id: String, val name: String) {
        open suspend fun getTables(): List<Table> = emptyList()
        override fun toString(): String = "Schema($id, $name)"
    }

    /** meta information database */
    abstract class Database(val id: String, val dbType: String? = null, val displayName: String? = null) {
        open suspend fun getSchemas(): List<Schema> = emptyList()
        override fun toString() = dbType?.let { "Database($id, $dbType, $displayName)" } ?: "Database($id)"
    }

    abstract class Configuration
}
