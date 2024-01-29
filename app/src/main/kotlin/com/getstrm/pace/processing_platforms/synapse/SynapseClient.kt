package com.getstrm.pace.processing_platforms.synapse

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.config.SynapseConfiguration
import com.getstrm.pace.domain.LeafResource
import com.getstrm.pace.domain.Resource
import com.getstrm.pace.exceptions.throwNotFound
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
import org.jooq.Queries
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class SynapseClient(
    config: SynapseConfiguration,
    private val jooq: DSLContext,
) : ProcessingPlatformClient(config) {
    // To match the behavior of the other ProcessingPlatform implementations, we connect to a
    // single database. If we want to add support for a single client to connect to multiple
    // databases, more info can be found here:
    // https://www.codejava.net/java-se/jdbc/how-to-list-names-of-all-databases-in-java
    constructor(
        config: SynapseConfiguration
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
        return listOf("database", "schema", "table").getOrElse(index) {
            super.platformResourceName(index)
        }
    }

    override suspend fun listChildren(pageParameters: PageParameters): PagedCollection<Resource> {
        return listDatabases()
    }

    override suspend fun getChild(childId: String): Resource {
        return listDatabases().find { it.id == childId }
            ?: throwNotFound(childId, "Synapse database")
    }

    override suspend fun transpilePolicy(dataPolicy: DataPolicy, renderFormatted: Boolean): String =
        queries(dataPolicy).sql

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

    private fun queries(dataPolicy: DataPolicy): Queries {
        val viewGenerator = SynapseViewGenerator(dataPolicy)
        // Explicitly dropping the views if they exist first and granting the 'select' to roles is
        // required by Synapse.
        val dropQuery = viewGenerator.dropViewsSQL().queries()
        val query = viewGenerator.toDynamicViewSQL().queries()
        val grantSelectQuery = viewGenerator.grantSelectPrivileges().queries()

        return jooq.queries(*dropQuery, *query, *grantSelectQuery)
    }

    fun listDatabases(): PagedCollection<Resource> {
        throwUnimplemented("listDatabases on Synapse")
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

    inner class SynapseDatabase(override val id: String, override val displayName: String = id) :
        Resource {
        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> {
            throwUnimplemented("listSchemas on Synapse")
        }

        override suspend fun getChild(childId: String): SynapseSchema {
            throwUnimplemented("getSchema on Synapse")
        }

        override fun fqn(): String {
            return id
        }
    }

    inner class SynapseSchema(
        val database: SynapseDatabase,
        override val id: String,
        override val displayName: String
    ) : Resource {
        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> {
            return jooq
                .meta()
                .filterSchemas { !schemasToIgnore.contains(it.name) }
                .tables
                .applyPageParameters(pageParameters)
                .map { SynapseTable(it, this, displayName, it.name) }
                .withPageInfo()
        }

        override suspend fun getChild(childId: String): SynapseTable {
            throwUnimplemented("getTable on Synapse")
        }

        override fun fqn(): String {
            return "${database.id}.$id"
        }
    }

    inner class SynapseTable(
        val table: org.jooq.Table<*>,
        val schema: SynapseSchema,
        override val id: String,
        override val displayName: String
    ) : LeafResource() {
        val fullName: String = "${table.schema?.name}.${table.name}"

        override fun fqn(): String = "${schema.fqn()}.${id}"

        override suspend fun createBlueprint(): DataPolicy {
            return DataPolicy.newBuilder()
                .setMetadata(
                    DataPolicy.Metadata.newBuilder()
                        .setTitle(fullName)
                        .setDescription(table.comment)
                )
                .setSource(
                    DataPolicy.Source.newBuilder()
                        .setRef(
                            resourceUrn {
                                integrationFqn = fullName
                                platform = apiProcessingPlatform
                            }
                        )
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
