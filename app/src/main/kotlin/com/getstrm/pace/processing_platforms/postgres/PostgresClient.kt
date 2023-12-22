package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform.PlatformType.POSTGRES
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.config.PPConfig
import com.getstrm.pace.config.PostgresConfig
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.withPageInfo
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

class PostgresClient(
    config: PPConfig,
    private val jooq: DSLContext,
) : ProcessingPlatformClient(config) {
    // To match the behavior of the other ProcessingPlatform implementations, we connect to a
    // single database. If we want to add support for a single client to connect to mulitple
    // databases, more info can be found here:
    // https://www.codejava.net/java-se/jdbc/how-to-list-names-of-all-databases-in-java
    constructor(config: PostgresConfig) : this(
        config,
        DSL.using(
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = config.getJdbcUrl()
                    username = config.userName
                    password = config.password
                }),
            SQLDialect.POSTGRES
        )
    )

    private val log by lazy { LoggerFactory.getLogger(javaClass) }


    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val viewGenerator = PostgresViewGenerator(dataPolicy)
        val query = viewGenerator.toDynamicViewSQL().sql

        withContext(Dispatchers.IO) {
            jooq.query(query).execute()
        }
    }

    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<Database> {
        TODO("Not yet implemented")
    }

    override suspend fun listSchemas(databaseId: String, pageParameters: PageParameters): PagedCollection<Schema> {
        TODO("Not yet implemented")
    }

    override suspend fun listTables(
        databaseId: String,
        schemaId: String,
        pageParameters: PageParameters
    ): PagedCollection<Table> {
        TODO("Not yet implemented")
    }

    override suspend fun getTable(databaseId: String, schemaId: String, tableId: String): Table {
        TODO("Not yet implemented")
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
        private val schemasToIgnore = listOf("information_schema", "pg_catalog")
    }

    inner class PostgresDatabase(override val platformClient: ProcessingPlatformClient, id: String)
        :Database(
        platformClient, id, POSTGRES.name, id
    ) {
        override suspend fun listSchemas(pageParameters: PageParameters): PagedCollection<Schema> {
            TODO("Not yet implemented")
        }

        override suspend fun getSchema(schemaId: String): Schema {
            TODO("Not yet implemented")
        }
    }
    inner class PostgresSchema(
        database: Database, id: String, name: String

    ) : Schema(
        database, id, name,
    ) {
        override suspend fun listTables(pageParameters: PageParameters): PagedCollection<Table> {
            TODO("Not yet implemented")
        }

        override suspend fun getTable(tableId: String): Table {
            TODO("Not yet implemented")
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
