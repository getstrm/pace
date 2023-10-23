package com.getstrm.pace.snowflake

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.databricks.sdk.service.catalog.ColumnInfo
import com.databricks.sdk.service.catalog.TableInfo
import com.getstrm.pace.databricks.toDataPolicy
import com.google.protobuf.Timestamp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DataPolicyConverterTest {
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
                        ColumnInfo()
                            .setName("column_without_nullable")
                            .setTypeText("bigint"),
                    ),
                )

        // When
        val policy = tableInfo.toDataPolicy(DataPolicy.ProcessingPlatform.getDefaultInstance())

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
                .setInfo(
                    DataPolicy.Info.newBuilder()
                        .setTitle("test_table")
                        .setDescription("test comment")
                        .setCreateTime(createdTimestamp)
                        .setUpdateTime(updateTimestamp),
                )
                .setPlatform(DataPolicy.ProcessingPlatform.getDefaultInstance())
                .setSource(
                    DataPolicy.Source.newBuilder()
                        .setRef("test_catalog.test_schema.test_table")
                        .setType(DataPolicy.Source.Type.DATABRICKS)
                        .addAllAttributes(
                            listOf(
                                DataPolicy.Attribute.newBuilder()
                                    .addAllPathComponents(listOf("test_string_column"))
                                    .setType("string")
                                    .setRequired(false)
                                    .build(),
                                DataPolicy.Attribute.newBuilder()
                                    .addAllPathComponents(listOf("test_bigint_column"))
                                    .setType("bigint")
                                    .setRequired(true)
                                    .build(),
                                DataPolicy.Attribute.newBuilder()
                                    .addAllPathComponents(listOf("column_without_nullable"))
                                    .setType("bigint")
                                    .setRequired(true)
                                    .build(),
                            ),
                        ),
                )
                .build()
    }
}
