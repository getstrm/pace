package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.AbstractDatabaseTest
import com.getstrm.pace.config.PostgresConfiguration
import com.getstrm.pace.domain.LeafResource
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.util.DEFAULT_PAGE_PARAMETERS
import com.getstrm.pace.util.pathString
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PostgresClientTest : AbstractDatabaseTest() {
    val config =
        PostgresConfiguration("postgres", "localhost", port, "postgres", "postgres", "postgres")
    private val underTest = PostgresClient(config)
    private val db = underTest.PostgresDatabase("postgres")

    @BeforeEach
    fun setupDatabase() {
        dataSource.executeMigrations("database/postgres-client")
    }

    @Test
    fun `list tables`() {
        runBlocking {
            // Given
            val schema = db.getChild("public")
            val tables = schema.listChildren()
            // Then
            tables.data.map { it.id } shouldContainExactlyInAnyOrder
                listOf("demo", "flyway_schema_history")
        }
    }

    @Test
    fun `list groups`() {
        // Given some groups in the database

        // When
        val (actual, pageInfo) =
            runBlocking { underTest.listGroups(DEFAULT_PAGE_PARAMETERS) }.map { it.copy(id = "") }

        // Then
        actual shouldBe
            listOf(
                Group("", "fraud_and_risk"),
                Group("", "marketing"),
            )
        pageInfo.total shouldBe 2
    }

    @Test
    fun `test tags from field comment`() {
        runBlocking {
            val schema = db.getChild("public")
            val tables = schema.getChild("demo") as LeafResource
            val policy: DataPolicy = tables.createBlueprint()
            val field = policy.source.fieldsList.find { it.pathString() == "email" }!!
            field.tagsList shouldContainExactlyInAnyOrder listOf("pii", "with whitespace", "email")
        }
    }

    companion object {
        private val ignoreTables =
            listOf("public.flyway_schema_history", "pace.data_policies", "pace.global_transforms")
    }
}
