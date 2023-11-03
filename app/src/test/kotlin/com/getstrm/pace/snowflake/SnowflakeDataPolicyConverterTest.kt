package com.getstrm.pace.snowflake

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SnowflakeDataPolicyConverterTest {
    @Test
    fun `convert snowflake describe table result`() {
        // Given
        val snowflakeResponse = SnowflakeResponse(
            resultSetMetaData = null,
            data = listOf(
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

        // When
        val policy = snowflakeResponse.toDataPolicy(platform, "test_schema.test_table")

        // Then
        policy shouldBe
            DataPolicy.newBuilder()
                .setMetadata(
                    DataPolicy.Metadata.newBuilder()
                        .setTitle("test_schema.test_table")
                )
                .setPlatform(platform)
                .setSource(
                    DataPolicy.Source.newBuilder()
                        .setRef("test_schema.test_table")
                        .addAllFields(
                            listOf(
                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf("test_nullable_varchar_column"))
                                    .setType("varchar")
                                    .setRequired(false)
                                    .build(),
                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf("test_required_varchar_column"))
                                    .setType("varchar")
                                    .setRequired(true)
                                    .build(),
                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf("test_number_column"))
                                    .setType("numeric")
                                    .setRequired(true)
                                    .build(),
                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf("test_timestamp_column"))
                                    .setType("varchar")
                                    .setRequired(false)
                                    .build(),
                            ),
                        ),
                )
                .build()
    }
}