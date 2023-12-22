package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform.PlatformType.POSTGRES
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.config.PostgresConfig
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

class PostgresClient( override val config: PostgresConfig ) : ProcessingPlatformClient(config) {
    // To match the behavior of the other ProcessingPlatform implementations, we connect to a
    // single database. If we want to add support for a single client to connect to mulitple
    // databases, more info can be found here:
    // https://www.codejava.net/java-se/jdbc/how-to-list-names-of-all-databases-in-java
        private val jooq = DSL.using(
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = config.getJdbcUrl()
                    username = config.userName
                    password = config.password
                }),
            SQLDialect.POSTGRES
        )
    val database = PostgresDatabase(this, config.database)

    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val viewGenerator = PostgresViewGenerator(dataPolicy)
        val query = viewGenerator.toDynamicViewSQL().sql

        withContext(Dispatchers.IO) {
            jooq.query(query).execute()
        }
    }

    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<Database> {
        return listOf(database).withPageInfo()
    }

    override suspend fun listSchemas(databaseId: String, pageParameters: PageParameters): PagedCollection<Schema> =
        (if (this.database.id == databaseId) this.database else throwNotFound(
            databaseId,
            "PostgreSQL database"
        )).listSchemas(pageParameters)

    override suspend fun listTables(
        databaseId: String,
        schemaId: String,
        pageParameters: PageParameters
    ): PagedCollection<Table> {
        val schema = listSchemas(databaseId).find{it.id == schemaId} ?: throwNotFound(schemaId, "PostgreSQL schema")
        return schema.listTables(pageParameters)
    }

    override suspend fun getTable(databaseId: String, schemaId: String, tableId: String): Table {
        // TODO improve performance
        return listTables(databaseId, schemaId, THOUSAND_RECORDS).find { it.id ==tableId } ?: throwNotFound(tableId, "PostgreSQL table")
    }

    override suspend fun listGroups(pageParameters: PageParameters): PagedCollection<Group> {
        val result = withContext(Dispatchers.IO) {
            jooq.select(DSL.field("oid", Int::class.java), DSL.field("rolname", String::class.java))
                .from(DSL.table("pg_roles"))
                .where(
                    DSL.field("rolcanlogin").notEqual(true),
                    DSL.field("rolname").notLike("pg_%")
                )
                .orderBy(DSL.field("rolname"))
                .offset(pageParameters.skip)
                .limit(pageParameters.pageSize)
                .fetch()
        }

        return result.map { (oid, rolname) -> Group(id = oid.toString(), name = rolname) }.withPageInfo()
    }

    companion object {
        // These are built-in Postgres schemas that we don't want to list tables from.
    }

    inner class PostgresDatabase(override val platformClient: ProcessingPlatformClient, id: String)
        :Database( platformClient, id, POSTGRES.name, id ) {
            
        override suspend fun listSchemas(pageParameters: PageParameters): PagedCollection<Schema> =
            jooq
            .meta()
            .schemas
            .filter { it.name !in listOf("information_schema", "pg_catalog") }
            .map { PostgresSchema(this, it.name, it.name) }
            .sortedBy { it.name }.withPageInfo()

        override suspend fun getSchema(schemaId: String): Schema =
            listSchemas(MILLION_RECORDS).find{it.id == schemaId}
            ?: throwNotFound(schemaId, "PostgreSQL schema")
    }
    
    inner class PostgresSchema( database: PostgresDatabase, id: String, name: String ) :
        Schema( database, id, name ) {
        override suspend fun listTables(pageParameters: PageParameters): PagedCollection<Table> {
            val tables = jooq
                .meta()
                .filterSchemas {it.name == id}
                .tables
                .map { PostgresTable(this, it) }
                .sortedBy { it.fullName }
                .applyPageParameters(pageParameters)
            return tables.withPageInfo()
        }

        override suspend fun getTable(tableId: String): Table {
             return listTables(MILLION_RECORDS).find{it.id == tableId}
                 ?: throwNotFound(tableId, "PostgreSQL table")
        }
    }

    inner class PostgresTable(
        schema: ProcessingPlatformClient.Schema,
        val table: org.jooq.Table<*>,
    ) : Table(
        schema, table.name, table.name
    ) {
        override val fullName: String = "${table.schema?.name}.${table.name}"

        override suspend fun createBlueprint(): DataPolicy {
            return DataPolicy.newBuilder()
                .setMetadata(
                    DataPolicy.Metadata.newBuilder()
                        .setTitle(fullName)
                        .setDescription(table.comment)
                )
                .setPlatform(schema.database.platformClient.apiProcessingPlatform)
                .setSource(
                    DataPolicy.Source.newBuilder()
                        .setRef(fullName)
                        .addAllFields(
                            table.fields().map { field ->
                                DataPolicy.Field.newBuilder()
                                    .addNameParts(field.name)
                                    .setType(field.dataType.typeName)
                                    .addAllTags(field.toTags())
                                    .setRequired(!field.dataType.nullable())
                                    .build().normalizeType()
                            },
                        )
                        .build(),
                )
                .build()
        }
    }
}

private val schemasToIgnore = listOf("information_schema", "pg_catalog")
