package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.POSTGRES
import com.getstrm.pace.config.PostgresConfig
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.processing_platforms.Table
import com.getstrm.pace.util.normalizeType
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

class PostgresClient(
    override val id: String,
    private val jooq: DSLContext,
) : ProcessingPlatformClient {
    // To match the behavior of the other ProcessingPlatform implementations, we connect to a
    // single database. If we want to add support for a single client to connect to mulitple
    // databases, more info can be found here:
    // https://www.codejava.net/java-se/jdbc/how-to-list-names-of-all-databases-in-java
    constructor(
        config: PostgresConfig
    ) : this(
        config.id,
        DSL.using(
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = config.getJdbcUrl()
                    username = config.userName
                    password = config.password
                }
            ),
            SQLDialect.POSTGRES
        )
    )

    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    override val type = POSTGRES

    override suspend fun listTables(): List<Table> =
        jooq
            .meta()
            .filterSchemas { !schemasToIgnore.contains(it.name) }
            .tables
            .map { PostgresTable(it) }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val viewGenerator = PostgresViewGenerator(dataPolicy)
        val query = viewGenerator.toDynamicViewSQL().sql

        withContext(Dispatchers.IO) { jooq.query(query).execute() }
    }

    override suspend fun listGroups(): List<Group> {
        val result =
            withContext(Dispatchers.IO) {
                jooq
                    .select(
                        DSL.field("oid", Int::class.java),
                        DSL.field("rolname", String::class.java)
                    )
                    .from(DSL.table("pg_roles"))
                    .where(
                        DSL.field("rolcanlogin").notEqual(true),
                        DSL.field("rolname").notLike("pg_%")
                    )
                    .fetch()
            }

        return result.map { (oid, rolname) -> Group(id = oid.toString(), name = rolname) }
    }

    companion object {
        // These are built-in Postgres schemas that we don't want to list tables from.
        private val schemasToIgnore = listOf("information_schema", "pg_catalog")
    }
}

class PostgresTable(val table: org.jooq.Table<*>) : Table() {
    override val fullName: String = "${table.schema?.name}.${table.name}"

    override suspend fun toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy {
        return DataPolicy.newBuilder()
            .setMetadata(
                DataPolicy.Metadata.newBuilder().setTitle(fullName).setDescription(table.comment)
            )
            .setPlatform(platform)
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
                                .build()
                                .normalizeType()
                        },
                    )
                    .build(),
            )
            .build()
    }
}
