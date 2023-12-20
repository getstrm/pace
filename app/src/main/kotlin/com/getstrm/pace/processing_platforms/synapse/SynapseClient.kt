package com.getstrm.pace.processing_platforms.synapse

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.SYNAPSE
import com.getstrm.pace.config.SynapseConfig
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

class SynapseClient(
    override val id: String,
    private val jooq: DSLContext,
) : ProcessingPlatformClient {
    // To match the behavior of the other ProcessingPlatform implementations, we connect to a
    // single database. If we want to add support for a single client to connect to multiple
    // databases, more info can be found here:
    // https://www.codejava.net/java-se/jdbc/how-to-list-names-of-all-databases-in-java
    constructor(
        config: SynapseConfig
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
            SQLDialect.DEFAULT
        ),
    )

    override val type = SYNAPSE

    override suspend fun listTables(): List<Table> =
        jooq
            .meta()
            .filterSchemas { !schemasToIgnore.contains(it.name) }
            .tables
            .map { SynapseTable(it) }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val viewGenerator = SynapseViewGenerator(dataPolicy)
        // Explicitly dropping the views if they exist first and granting the 'select' to roles is
        // required by Synapse.
        val dropQuery = viewGenerator.dropViewsSQL().queries()
        val query = viewGenerator.toDynamicViewSQL().queries()
        val grantSelectQuery = viewGenerator.grantSelectPrivileges().queries()

        withContext(Dispatchers.IO) {
            jooq.queries(*dropQuery, *query, *grantSelectQuery).executeBatch()
        }
    }

    override suspend fun listGroups(): List<Group> {
        val result =
            withContext(Dispatchers.IO) {
                jooq
                    .select(
                        DSL.field("principal_id", Int::class.java),
                        DSL.field("name", String::class.java)
                    )
                    .from(DSL.table("sys.database_principals"))
                    .where(DSL.field("type_desc").eq("DATABASE_ROLE"))
                    .fetch()
            }

        return result.map { (oid, rolname) -> Group(id = oid.toString(), name = rolname) }
    }

    companion object {
        // These are built-in Synapse schemas that we don't want to list tables from.
        private val schemasToIgnore = listOf("sys", "INFORMATION_SCHEMA")
    }
}

class SynapseTable(val table: org.jooq.Table<*>) : Table() {
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
