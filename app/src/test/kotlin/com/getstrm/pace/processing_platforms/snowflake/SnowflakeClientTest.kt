package com.getstrm.pace.processing_platforms.snowflake

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.config.SnowflakeConfig
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

class SnowflakeClientTest {

    private val config =
        SnowflakeConfig(
            id = "",
            serverUrl = "",
            database = "PACE",
            warehouse = "COMPUTE_WH",
            userName = "",
            accountName = "",
            organizationName = "",
            privateKey = "",
        )

    @Test
    fun `convert snowflake describe table result`() {
        // Given
        val snowflakeClient = mockk<SnowflakeClient>()
        val snowflakeResponse =
            SnowflakeResponse(
                resultSetMetaData = null,
                data =
                    listOf(
                        listOf("test_nullable_varchar_column", "VARCHAR(16777216)", "unused", "y"),
                        listOf("test_required_varchar_column", "VARCHAR(16777216)", "unused", "n"),
                        listOf("test_number_column", "NUMBER(38,0)", "unused", "n"),
                        listOf("test_timestamp_column", "TIMESTAMP_NTZ(9)", "unused", "y"),
                    ),
                code = "1234",
                createdOn = null,
                message = null,
            )
        val platform = DataPolicy.ProcessingPlatform.newBuilder().setId("test-platform").build()

        every { snowflakeClient.describeTable("test_schema", "test_table") } returns
            snowflakeResponse

        every { snowflakeClient.executeRequest(any()) } returns
            ResponseEntity<SnowflakeResponse>(
                SnowflakeResponse(
                    resultSetMetaData = null,
                    data = listOf(listOf("pii", "ok")),
                    code = "123",
                    createdOn = null,
                    message = "Statement executed successfully.",
                ),
                HttpStatus.OK
            )

        val table =
            SnowflakeTable(
                "test_schema.test_table",
                config,
                "test_table",
                "test_schema",
                snowflakeClient
            )

        // When
        val policy = runBlocking { table.toDataPolicy(platform) }

        // Then
        policy shouldBe
            DataPolicy.newBuilder()
                .setMetadata(DataPolicy.Metadata.newBuilder().setTitle("test_schema.test_table"))
                .setPlatform(platform)
                .setSource(
                    DataPolicy.Source.newBuilder()
                        .setRef("test_schema.test_table")
                        .addAllFields(
                            listOf(
                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf("test_nullable_varchar_column"))
                                    .addAllTags(listOf("pii"))
                                    .setType("varchar")
                                    .setRequired(false)
                                    .build(),
                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf("test_required_varchar_column"))
                                    .addAllTags(listOf("pii"))
                                    .setType("varchar")
                                    .setRequired(true)
                                    .build(),
                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf("test_number_column"))
                                    .addAllTags(listOf("pii"))
                                    .setType("numeric")
                                    .setRequired(true)
                                    .build(),
                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf("test_timestamp_column"))
                                    .addAllTags(listOf("pii"))
                                    .setType("varchar")
                                    .setRequired(false)
                                    .build(),
                            ),
                        ),
                )
                .build()
    }
}
