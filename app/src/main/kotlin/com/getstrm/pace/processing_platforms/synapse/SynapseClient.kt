package com.getstrm.pace.processing_platforms.synapse

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataResourceRef
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.config.SynapseConfig
import com.getstrm.pace.exceptions.throwUnimplemented
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.applyPageParameters
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.withPageInfo
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class SynapseClient(
    config: SynapseConfig,
    private val jooq: DSLContext,
) : ProcessingPlatformClient(config) {
    // To match the behavior of the other ProcessingPlatform implementations, we connect to a
    // single database. If we want to add support for a single client to connect to multiple
    // databases, more info can be found here:
    // https://www.codejava.net/java-se/jdbc/how-to-list-names-of-all-databases-in-java
    constructor(
        config: SynapseConfig
    ) : this(
        config,
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

    override suspend fun platformResourceName(index: Int): String {
        // TODO this should be updated, as the implementation is missing for Synapse to list the
        // various levels in the hierarchy
        return when (index) {
            0 -> "database"
            1 -> "schema"
            2 -> "table"
            else -> throw IllegalArgumentException("Unsupported index: $index")
        }
    }

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

    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<Database> {
        throwUnimplemented("listDatabases on Synapse")
    }

    override suspend fun listSchemas(
        databaseId: String,
        pageParameters: PageParameters
    ): PagedCollection<Schema> {
        throwUnimplemented("listSchemas on Synapse")
    }

    override suspend fun listTables(
        databaseId: String,
        schemaId: String,
        pageParameters: PageParameters
    ): PagedCollection<Table> {
        throwUnimplemented("listTables on Synapse")
    }

    override suspend fun getTable(databaseId: String, schemaId: String, tableId: String): Table {
        throwUnimplemented("getTable on Synapse")
    }

    override suspend fun listGroups(pageParameters: PageParameters): PagedCollection<Group> {
        val result =
            withContext(Dispatchers.IO) {
                jooq
                    .select(
                        DSL.field("principal_id", Int::class.java),
                        DSL.field("name", String::class.java)
                    )
                    .from(DSL.table("sys.database_principals"))
                    .where(DSL.field("type_desc").eq("DATABASE_ROLE"))
                    .limit(pageParameters.pageSize)
                    .offset(pageParameters.skip)
                    .fetch()
            }
        return result
            .map { (oid, rolname) -> Group(id = oid.toString(), name = rolname) }
            .withPageInfo()
    }

    companion object {
        // These are built-in Synapse schemas that we don't want to list tables from.
        private val schemasToIgnore = listOf("sys", "INFORMATION_SCHEMA")
    }

    inner class SynapseDatabase(pp: ProcessingPlatformClient, id: String) :
        ProcessingPlatformClient.Database(pp, id, ProcessingPlatform.PlatformType.SYNAPSE) {
        override suspend fun listSchemas(pageParameters: PageParameters): PagedCollection<Schema> {
            throwUnimplemented("listSchemas on Synapse")
        }

        override suspend fun getSchema(schemaId: String): Schema {
            throwUnimplemented("getSchema on Synapse")
        }
    }

    inner class SynapseSchema(database: Database, id: String, name: String) :
        ProcessingPlatformClient.Schema(database, id, name) {
        override suspend fun listTables(pageParameters: PageParameters): PagedCollection<Table> {
            return jooq
                .meta()
                .filterSchemas { !schemasToIgnore.contains(it.name) }
                .tables
                .applyPageParameters(pageParameters)
                .map { it -> SynapseTable(it, this, name, it.name) }
                .withPageInfo()
        }

        override suspend fun getTable(tableId: String): Table {
            throwUnimplemented("getTable on Synapse")
        }
    }

    inner class SynapseTable(
        val table: org.jooq.Table<*>,
        schema: Schema,
        id: String,
        name: String
    ) : Table(schema, id, name) {
        override val fullName: String = "${table.schema?.name}.${table.name}"

        override suspend fun createBlueprint(): DataPolicy {
            return DataPolicy.newBuilder()
                .setMetadata(
                    DataPolicy.Metadata.newBuilder()
                        .setTitle(fullName)
                        .setDescription(table.comment)
                )
                .setSource(
                    DataPolicy.Source.newBuilder()
                        .setRef(DataResourceRef.newBuilder().setPlatformFqn(fullName).build())
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
}
