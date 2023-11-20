package com.getstrm.pace.processing_platforms.synapse

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.POSTGRES
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.SYNAPSE
import com.getstrm.pace.config.SynapseConfig
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.processing_platforms.Table
import com.getstrm.pace.util.normalizeType
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

class SynapseClient(
    override val id: String,
    private val jooq: DSLContext,
) : ProcessingPlatformClient {
    // To match the behavior of the other ProcessingPlatform implementations, we connect to a
    // single database. If we want to add support for a single client to connect to mulitple
    // databases, more info can be found here:
    // https://www.codejava.net/java-se/jdbc/how-to-list-names-of-all-databases-in-java
    constructor(config: SynapseConfig) : this(
        config.id,
        DSL.using(
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = config.getJdbcUrl()
                    username = config.userName
                    password = config.password
                }),
            SQLDialect.MYSQL
        )
    )

    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    override val type = SYNAPSE

    override suspend fun listTables(): List<Table> = jooq.meta().tables
        .filter { !schemasToIgnore.contains(it.schema?.name) }
        .map { SynapseTable(it) }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
//        val viewGenerator = PostgresViewGenerator(dataPolicy)
//        val query = viewGenerator.toDynamicViewSQL()
//
//        withContext(Dispatchers.IO) {
//            jooq.query(query).execute()
//        }
        TODO()
    }

    override suspend fun listGroups(): List<Group> {
        return emptyList()
    }

    companion object {
        // These are built-in Postgres schemas that we don't want to list tables from.
        private val schemasToIgnore = listOf("sys", "INFORMATION_SCHEMA")
    }
}

class SynapseTable(
    val table: org.jooq.Table<*>
) : Table() {
    override val fullName: String = "${table.schema?.name}.${table.name}"

    override suspend fun toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy {
        return DataPolicy.newBuilder()
            .setMetadata(
                DataPolicy.Metadata.newBuilder()
                    .setTitle(fullName)
                    .setDescription(table.comment)
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
                                .build().normalizeType()
                        },
                    )
                    .build(),
            )
            .build()
    }

    companion object {
        private val regex = """(?:pace)\:\:((?:\"[\w\s\-\_]+\"|[\w\-\_]+))""".toRegex()
        private fun <T> Field<T>.toTags(): List<String> {
            val match = regex.findAll(comment)
            return match.map {
                it.groupValues[1].trim('"')
            }.toList()
        }
    }
}
