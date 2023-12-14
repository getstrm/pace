package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.AbstractDatabaseTest
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.util.DEFAULT_PAGE_PARAMETERS
import com.getstrm.pace.util.pathString
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
        val actual = runBlocking { underTest.listTables(DEFAULT_PAGE_PARAMETERS) }.filter { !ignoreTables.contains(it.fullName) }
        val tableNames = actual.map { it.fullName }

        // Then
        tableNames shouldBe listOf("public.demo")
    }

    @Test
    fun `list groups`() {
        // Given some groups in the database

        // When
        val actual = runBlocking { underTest.listGroups(DEFAULT_PAGE_PARAMETERS) }.map { it.copy(id = "") }

        // Then
        actual shouldBe listOf(
            Group("", "fraud_and_risk"),
            Group("", "marketing"),
        )
    }

    @Test
    fun `test tags from field comment`() {
        // Given a table in the database

        // When
        val actual = runBlocking { underTest.listTables(DEFAULT_PAGE_PARAMETERS) }.filter { !ignoreTables.contains(it.fullName) }
        runBlocking {
            val policy = actual.map { it.toDataPolicy(DataPolicy.ProcessingPlatform.getDefaultInstance()) }.first()
            val field = policy.source.fieldsList.find { it.pathString() == "email" }!!
            field.tagsList shouldContainExactlyInAnyOrder listOf("pii", "with whitespace", "email")
        }
    }

    companion object {
        private val ignoreTables =
            listOf("public.flyway_schema_history", "pace.data_policies", "pace.global_transforms")
    }
}
