package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Aggregation
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Hash
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.namedField
import com.getstrm.pace.toSql
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BigQueryTransformerTest {

    private val underTest = BigQueryTransformer

    @Test
    fun `names should be rendered with backticks`() {
        underTest.renderName("my-project.my_dataset.my_table") shouldBe
            "`my-project.my_dataset.my_table`"
    }

    @Test
    fun `regexp extract`() {
        // Given
        val field = namedField("my_field")
        val regexp =
            DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder()
                .setRegexp("foo(bar)")
                .build()

        // When
        val result = underTest.regexpReplace(field, regexp)

        // Then
        result.toSql() shouldBe "regexp_extract(my_field, 'foo(bar)')"
    }

    @Test
    fun `aggregation - avg`() {
        // Given
        val avgField = namedField("transactionamount", "integer")
        val groupByFields = listOf(namedField("brand", "varchar"), namedField("age", "integer"))
        val avgAggregation =
            Aggregation.newBuilder()
                .setAvg(Aggregation.Avg.getDefaultInstance())
                .addAllPartitionBy(groupByFields)
                .build()

        // When
        val result = underTest.aggregation(avgField, avgAggregation)

        // Then
        result.toSql() shouldBe
            "avg(cast(`transactionamount` as decimal)) over(partition by `brand`, `age`)"
    }

    @Test
    fun `hash - string`() {
        // Given
        val field = namedField("email", "string")
        val hash = Hash.getDefaultInstance()

        // When
        val result = underTest.hash(field, hash)

        // Then
        result.toSql() shouldBe "TO_HEX(SHA256(CAST(`email` AS STRING)))"
    }

    @Test
    fun `hash - numerical`() {
        // Given
        val field = namedField("transactionamount", "integer")
        val hash = Hash.getDefaultInstance()

        // When
        val result = underTest.hash(field, hash)

        // Then
        result.toSql() shouldBe "FARM_FINGERPRINT(CAST(`transactionamount` AS STRING))"
    }

    @Test
    fun `hash - not valid`() {
        // Given
        val field = namedField("timestamp", "timestamp")
        val hash = Hash.getDefaultInstance()

        // Then
        shouldThrow<InternalException> { underTest.hash(field, hash) }
    }

    @Test
    fun `regexp replace should use double backslashes for capturing group backreferences`() {
        // Given
        val field = namedField("my_field")
        val regexp =
            DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder()
                .setRegexp("foo(\\w+)")
                .setReplacement("baz$1")
                .build()

        // When
        val result = underTest.regexpReplace(field, regexp)

        // Then
        result.toSql() shouldBe """regexp_replace(my_field, 'foo(\w+)', 'baz\\1')"""
    }

    @Test
    fun `detokenize should use backticked names`() {
        // Given
        val tokenizedField = namedField("tokenized_field")
        val detokenize =
            DataPolicy.RuleSet.FieldTransform.Transform.Detokenize.newBuilder()
                .setTokenField(namedField("token_field"))
                .setValueField(namedField("value_field"))
                .setTokenSourceRef("my-project.my_token_dataset.my_tokens")
                .build()

        // When
        val result =
            underTest.detokenize(tokenizedField, detokenize, "my-project.my_dataset.my_table")

        // Then
        result.toSql() shouldBe
            "coalesce(`my-project.my_token_dataset.my_tokens`.`value_field`, `my-project.my_dataset.my_table`.`tokenized_field`)"
    }
}
