package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.processing_platforms.AbstractDynamicViewGenerator
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
import org.junit.jupiter.api.BeforeAll
import org.slf4j.LoggerFactory
import javax.sql.DataSource

// We wrap the field in a select to get a sql with bound values
fun Field<*>.toSql(): String = DSL.select(this).getSQL(ParamType.INLINED).removePrefix("select ")

class TestDynamicViewGenerator(dataPolicy: DataPolicy) : AbstractDynamicViewGenerator(dataPolicy) {
    override fun List<DataPolicy.Principal>.toPrincipalCondition(): Condition? {
        return null
    }
}

fun String.toPrincipal() = DataPolicy.Principal.newBuilder().setGroup(this).build()
fun List<String>.toPrincipals() = map { it.toPrincipal() }

abstract class AbstractDatabaseTest {
    companion object {
        private val log by lazy { LoggerFactory.getLogger(AbstractDatabaseTest::class.java) }
        lateinit var dataSource: DataSource
        lateinit var jooq: DSLContext

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

                        HikariDataSource(createHikariConfig(embedded.port))
                    }.executeMigrations("db/migration/common", "db/migration/dev")
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
