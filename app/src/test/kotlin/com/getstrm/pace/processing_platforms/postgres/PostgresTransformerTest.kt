package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.namedField
import com.getstrm.pace.toSql
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PostgresTransformerTest {

    private val underTest = PostgresTransformer()

    @Test
    fun `regexp replace should use a single backtick for capturing group backreferences`() {
        // Given
        val field = namedField("my_field")
        val regexp =
            DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder()
                .setRegexp("foo(\\w+) bar(\\w+)")
                .setReplacement("fizz$1buzz$2baz$")
                .build()

        // When
        val result = underTest.regexpReplace(field, regexp)

        // Then
        result.toSql() shouldBe
            """regexp_replace(my_field, 'foo(\w+) bar(\w+)', 'fizz\1buzz\2baz$')"""
    }

    @Test
    fun `regexp extract uses substring from pattern`() {
        // Given
        val field = namedField("my_field")
        val regexp =
            DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder()
                .setRegexp("@(\\w+).com")
                .build()

        // When
        val result = underTest.regexpReplace(field, regexp)

        // Then
        result.toSql() shouldBe "substring(my_field from '@(\\w+).com')"
    }

    @Test
    fun `aggregation - avg with 0 precision`() {
        // Given
        val avgField = namedField("transactionamount", "integer")
        val partitionByFields = listOf(namedField("brand", "varchar"), namedField("age", "integer"))
        val avgAggregation =
            DataPolicy.RuleSet.FieldTransform.Transform.Aggregation.newBuilder()
                .addAllPartitionBy(partitionByFields)
                .setAvg(
                    DataPolicy.RuleSet.FieldTransform.Transform.Aggregation.Avg.newBuilder()
                        .setPrecision(0)
                )
                .build()

        // When
        val result = underTest.aggregation(avgField, avgAggregation)

        // Then
        result.toSql() shouldBe
            "round(avg(cast(transactionamount as decimal)) over(partition by brand, age), 0)"
    }
}
