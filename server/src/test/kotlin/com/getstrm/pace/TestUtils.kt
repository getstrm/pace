package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeAll
import org.slf4j.LoggerFactory

fun namedField(name: String, type: String? = null): DataPolicy.Field =
    DataPolicy.Field.newBuilder()
        .addNameParts(name)
        .apply { if (type != null) setType(type) }
        .build()

fun String.toPrincipal(): DataPolicy.Principal =
    DataPolicy.Principal.newBuilder().setGroup(this).build()

abstract class AbstractDatabaseTest {
    companion object {
        private val log by lazy { LoggerFactory.getLogger(AbstractDatabaseTest::class.java) }
        lateinit var dataSource: DataSource
        lateinit var jooq: DSLContext
        var port: Int = 5452

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
                        }
                        .executeMigrations("db/migration/postgresql")
            }

            if (!::jooq.isInitialized) {
                jooq = DSL.using(dataSource, SQLDialect.POSTGRES)
            }
        }

        private fun createHikariConfig(port: Int = 5432) =
            HikariConfig().apply {
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
