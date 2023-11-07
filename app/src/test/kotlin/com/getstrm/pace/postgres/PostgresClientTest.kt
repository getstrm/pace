package com.getstrm.pace.postgres

import com.getstrm.pace.AbstractDatabaseTest
import com.getstrm.pace.processing_platforms.Group
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PostgresClientTest : AbstractDatabaseTest() {
    private val underTest = PostgresClient("postgres", jooq)

    @BeforeEach
    fun setupDatabase() {
        dataSource.executeMigrations("database/postgres-client")
    }

    @Test
    fun `list tables`() {
        // Given a table in the database

        // When
        val actual = runBlocking { underTest.listTables() }.filter { it.fullName != "public.flyway_schema_history" }
        val tableNames = actual.map { it.fullName }

        // Then
        tableNames shouldBe listOf("public.demo")
    }

    @Test
    fun `list groups`() {
        // Given some groups in the database

        // When
        val actual = runBlocking { underTest.listGroups() }.map { it.copy(id = "") }

        // Then
        actual shouldBe listOf(
            Group("", "marketing"),
            Group("", "fraud_and_risk"),
        )
    }
}
