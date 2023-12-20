package com.getstrm.pace.processing_platforms.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.databricks.sdk.service.catalog.ColumnInfo
import com.databricks.sdk.service.catalog.TableInfo
import com.databricks.sdk.service.sql.ExecuteStatementResponse
import com.databricks.sdk.service.sql.ResultData
import com.databricks.sdk.service.sql.StatementState
import com.databricks.sdk.service.sql.StatementStatus
import com.google.protobuf.Timestamp
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class DatabricksClientTest {

    val client = mockk<DatabricksClient>()

    @Test
    fun `convert full table info`() {
        // Given
        val createdAt = 1695803510859
        val updatedAt = 1695803610859

        val tableInfo =
            TableInfo()
                .setName("test_table")
                .setComment("test comment")
                .setCreatedAt(createdAt)
                .setUpdatedAt(updatedAt)
                .setFullName("test_catalog.test_schema.test_table")
                .setColumns(
                    listOf(
                        ColumnInfo()
                            .setName("test_string_column")
                            .setTypeText("string")
                            .setNullable(true),
                        ColumnInfo()
                            .setName("test_bigint_column")
                            .setTypeText("bigint")
                            .setNullable(false),
                        ColumnInfo().setName("column_without_nullable").setTypeText("bigint")
                    )
                )

        every { client.executeStatement(any()) } returns
            ExecuteStatementResponse()
                .setStatus(StatementStatus().setState(StatementState.SUCCEEDED))
                .setResult(
                    ResultData()
                        .setDataArray(
                            listOf(
                                listOf("test_string_column", "pii"),
                                listOf("test_bigint_column", "tag1"),
                                listOf("test_string_column", "email")
                            )
                        )
                )
        val table = DatabricksTable(tableInfo.name, tableInfo, client)

        val platform = DataPolicy.ProcessingPlatform.newBuilder().setId("test-platform").build()

        // When
        val policy = runBlocking { table.toDataPolicy(platform) }

        // Then
        val createdTimestamp =
            Timestamp.newBuilder()
                .setSeconds(createdAt / 1000)
                .setNanos((createdAt % 1000 * 1000000).toInt())
                .build()
        val updateTimestamp =
            Timestamp.newBuilder()
                .setSeconds(updatedAt / 1000)
                .setNanos((updatedAt % 1000 * 1000000).toInt())
                .build()
        policy shouldBe
            DataPolicy.newBuilder()
                .setMetadata(
                    DataPolicy.Metadata.newBuilder()
                        .setTitle("test_table")
                        .setDescription("test comment")
                        .setCreateTime(createdTimestamp)
                        .setUpdateTime(updateTimestamp)
                )
                .setPlatform(platform)
                .setSource(
                    DataPolicy.Source.newBuilder()
                        .setRef("test_catalog.test_schema.test_table")
                        .addAllFields(
                            listOf(
                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf("test_string_column"))
                                    .addAllTags(listOf("pii", "email"))
                                    .setType("varchar")
                                    .setRequired(false)
                                    .build(),
                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf("test_bigint_column"))
                                    .addAllTags(listOf("tag1"))
                                    .setType("bigint")
                                    .setRequired(true)
                                    .build(),
                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf("column_without_nullable"))
                                    .setType("bigint")
                                    .setRequired(true)
                                    .build(),
                            )
                        )
                )
                .build()
    }
}
