package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.TransformCase.TAG_TRANSFORM
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.conf.ParamType
import org.jooq.impl.DSL
import org.jooq.impl.DSL.trueCondition
import org.junit.jupiter.api.BeforeAll
import org.slf4j.LoggerFactory
import javax.sql.DataSource

// We wrap the field in a select to get a sql with bound values
// Note: this is uses default settings (e.g. dialect).
fun Field<*>.toSql(): String = DSL.select(this).getSQL(ParamType.INLINED).removePrefix("select ")

fun namedField(name: String, type: String? = null): DataPolicy.Field = DataPolicy.Field.newBuilder()
    .addNameParts(name).apply {
        if (type != null) setType(type)
    }.build()

class TestDynamicViewGenerator(dataPolicy: DataPolicy) : ProcessingPlatformViewGenerator(dataPolicy) {
    override val jooq: DSLContext = DSL.using(SQLDialect.DEFAULT)

    override fun toPrincipalCondition(principals: List<DataPolicy.Principal>): Condition? {
        return null
    }

    override fun DataPolicy.RuleSet.Filter.RetentionFilter.Condition.toRetentionCondition(field: DataPolicy.Field): Condition {
        return trueCondition()
    }
}

fun String.toPrincipal() = DataPolicy.Principal.newBuilder().setGroup(this).build()
fun List<String>.toPrincipals() = map { it.toPrincipal() }

abstract class AbstractDatabaseTest {
    companion object {
        private val log by lazy { LoggerFactory.getLogger(AbstractDatabaseTest::class.java) }
        lateinit var dataSource: DataSource
        lateinit var jooq: DSLContext
        var port: Int = 5432

        @BeforeAll
        @JvmStatic
        fun setupAll() {
            val runningInCi = System.getProperty("ciBuild") != null

            if (!::dataSource.isInitialized) {
                dataSource =
                    if (runningInCi) {
                        log.info("Starting real postgres...")
                        HikariDataSource(createHikariConfig())
                    } else {
                        log.info("Starting EmbeddedPostgres...")
                        val embedded = EmbeddedPostgres.start()
                        port = embedded.port

                        HikariDataSource(createHikariConfig(embedded.port))
                    }.executeMigrations("db/migration/postgresql")
            }

            if (!::jooq.isInitialized) {
                jooq = DSL.using(dataSource, SQLDialect.POSTGRES)
            }
        }

        private fun createHikariConfig(port: Int = 5432) = HikariConfig()
            .apply {
                jdbcUrl = "jdbc:postgresql://localhost:$port/postgres"
                username = "postgres"
                password = "postgres"
                isAutoCommit = true
                connectionInitSql = "set time zone 'UTC';"
            }

        fun DataSource.executeMigrations(vararg locations: String): DataSource =
            this.also {
                Flyway.configure()
                    .loggers("slf4j")
                    .dataSource(it)
                    .locations(*locations)
                    .load()
                    .migrate()
            }
    }
}
