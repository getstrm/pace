package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.AbstractDatabaseTest
import com.getstrm.pace.config.PostgresConfig
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.util.DEFAULT_PAGE_PARAMETERS
import com.getstrm.pace.util.pathString
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class PostgresClientTest : AbstractDatabaseTest() {
    val config = PostgresConfig("postgres", "localhost", 5432, "postgres", "postgres", "postgres")
    private val underTest = PostgresClient(config, jooq)
    private val db = underTest.PostgresDatabase(underTest, "db")
    private val schema = underTest.PostgresSchema(db, "db")

    @BeforeEach
    fun setupDatabase() {
        dataSource.executeMigrations("database/postgres-client")
    }

    // Todo: re-enable when implemented
    @Disabled
    @Test
    fun `list tables`() {
        // Given a table in the database

        // When
        val actual = runBlocking { schema.listTables(DEFAULT_PAGE_PARAMETERS) }.data.filter { !ignoreTables.contains(it.fullName) }
        val tableNames = actual.map { it.fullName }

        // Then
        tableNames shouldBe listOf("public.demo")
    }

    @Test
    fun `list groups`() {
        // Given some groups in the database

        // When
        val (actual, pageInfo) = runBlocking { underTest.listGroups(DEFAULT_PAGE_PARAMETERS) }.map { it.copy(id = "") }

        // Then
        actual shouldBe listOf(
            Group("", "fraud_and_risk"),
            Group("", "marketing"),
        )
        pageInfo.total shouldBe 2
    }

    // Todo: re-enable when implemented
    @Disabled
    @Test
    fun `test tags from field comment`() {
        // Given a table in the database

        // When
        val actual = runBlocking { schema.listTables(DEFAULT_PAGE_PARAMETERS) }.data.filter { !ignoreTables.contains(it.fullName) }
        runBlocking {
            val policy: DataPolicy = actual.map { it.createBlueprint() }.first()
            val field = policy.source.fieldsList.find { it.pathString() == "email" }!!
            field.tagsList shouldContainExactlyInAnyOrder listOf("pii", "with whitespace", "email")
        }
    }

    companion object {
        private val ignoreTables =
            listOf("public.flyway_schema_history", "pace.data_policies", "pace.global_transforms")
    }
}
