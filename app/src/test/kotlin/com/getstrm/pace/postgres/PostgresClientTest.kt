package com.getstrm.pace.postgres

import com.getstrm.pace.AbstractDatabaseTest
import com.getstrm.pace.config.PostgresConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class PostgresClientTest : AbstractDatabaseTest() {
    private val underTest = PostgresClient("postgres", jooq)

    @BeforeEach
    fun setupDatabase() {
        dataSource.executeMigrations("database/postgres-client")
    }


}
