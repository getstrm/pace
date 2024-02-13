package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.config.PostgresConfiguration
import com.getstrm.pace.domain.LeafResource
import com.getstrm.pace.domain.Resource
import com.getstrm.pace.exceptions.throwNotFound
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
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * Mapping of concepts
 *
 * PACE Postgres Database Single Postgres Database Schema Postgres schema Table Postgres table.
 */
class PostgresClient(override val config: PostgresConfiguration) :
    ProcessingPlatformClient(config) {
    // To match the behavior of the other ProcessingPlatform implementations, we connect to a
    // single database. If we want to add support for a single client to connect to multiple
    // databases, more info can be found here:
    // https://www.codejava.net/java-se/jdbc/how-to-list-names-of-all-databases-in-java
    private val jooq =
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
    val database = PostgresDatabase(config.database)

    override suspend fun platformResourceName(index: Int) =
        listOf("database", "schema", "table").getOrElse(index) { super.platformResourceName(index) }

    override suspend fun transpilePolicy(dataPolicy: DataPolicy, renderFormatted: Boolean): String =
        PostgresViewGenerator(dataPolicy) {
                if (renderFormatted) {
                    withRenderFormatted(true)
                }
            }
            .toDynamicViewSQL()
            .sql

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val query = transpilePolicy(dataPolicy)
        withContext(Dispatchers.IO) { jooq.query(query).execute() }
    }

    fun listDatabases(): PagedCollection<Resource> = listOf(database).withPageInfo()

    override suspend fun listGroups(pageParameters: PageParameters): PagedCollection<Group> {
        val oidsAndRoles =
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
                    .orderBy(DSL.field("rolname"))
                    .offset(pageParameters.skip)
                    .limit(pageParameters.pageSize)
                    .fetch()
            }

        return oidsAndRoles
            .map { (oid, rolname) -> Group(id = oid.toString(), name = rolname) }
            .withPageInfo()
    }

    override suspend fun listChildren(pageParameters: PageParameters): PagedCollection<Resource> =
        listDatabases()

    override suspend fun getChild(childId: String): Resource = getDatabase(childId)

    private fun getDatabase(databaseId: String) =
        (if (this.database.id == databaseId) this.database
        else throwNotFound(databaseId, "PostgreSQL database"))

    inner class PostgresDatabase(override val id: String) : Resource {

        override val displayName = id

        override fun fqn(): String = id

        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> =
            jooq
                .meta()
                .schemas
                .filter { it.name !in listOf("information_schema", "pg_catalog") }
                .map { PostgresSchema(this, it.name) }
                .sortedBy { it.displayName }
                .withPageInfo()

        override suspend fun getChild(childId: String): PostgresSchema =
            (jooq.meta().schemas.firstOrNull { it.name == childId }
                    ?: throwNotFound(childId, "PostgreSQL schema"))
                .let { PostgresSchema(this, it.name) }
    }

    inner class PostgresSchema(
        val database: PostgresDatabase,
        override val id: String,
        override val displayName: String = id
    ) : Resource {
        override fun fqn(): String = id

        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> =
            jooq
                .meta()
                .filterSchemas { it.name == id }
                .tables
                .map { PostgresTable(this, it) }
                .sortedBy { it.fullName }
                .applyPageParameters(pageParameters)
                .withPageInfo()

        override suspend fun getChild(childId: String): PostgresTable =
            (jooq.meta().filterSchemas { it.name == id }.tables.firstOrNull { it.name == childId }
                    ?: throwNotFound(childId, "PostgreSQL table"))
                .let { PostgresTable(this, it) }
    }

    inner class PostgresTable(
        val schema: PostgresSchema,
        val table: org.jooq.Table<*>,
    ) : LeafResource() {
        override val id = table.name
        override val displayName = id

        override fun fqn(): String = "${schema.id}.$id"

        val fullName: String = "${table.schema?.name}.${table.name}"

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
                )
                .build()
        }
    }
}
