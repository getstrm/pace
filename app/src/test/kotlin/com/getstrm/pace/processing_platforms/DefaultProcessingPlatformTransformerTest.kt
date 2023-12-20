package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Aggregation
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.namedField
import com.getstrm.pace.toSql
import com.google.rpc.BadRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test

class DefaultProcessingPlatformTransformerTest {

    @Test
    fun `render name`() {
        DefaultProcessingPlatformTransformer.renderName("myschema.my_field") shouldBe "myschema.my_field"
    }

    @Test
    fun `regexp extract`() {
        // Given
        val field = namedField("my_field")
        val regexp = DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder()
            .setRegexp("foo(bar)")
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.regexpReplace(field, regexp)

        // Then
        result.toSql() shouldBe "regexp_extract(my_field, 'foo(bar)')"
    }

    @Test
    fun `regexp replace`() {
        // Given
        val field = namedField("my_field")
        val regexp = DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder()
            .setRegexp("foo(\\w+)")
            .setReplacement("baz$1")
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.regexpReplace(field, regexp)

        // Then
        result.toSql() shouldBe "regexp_replace(my_field, 'foo(\\w+)', 'baz$1')"
    }

    @Test
    fun `fixed string with string field`() {
        // Given
        val field = namedField("my_string", "string")
        val fixed = DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder()
            .setValue("foo")
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.fixed(field, fixed)

        // Then
        result shouldBe DSL.`val`("foo")
    }

    @Test
    fun `fixed int with string field`() {
        // Given
        val field = namedField("my_string", "string")
        val fixed = DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder()
            .setValue("1234")
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.fixed(field, fixed)

        // Then
        result shouldBe DSL.`val`("1234")
    }

    @Test
    fun `fixed int with int field`() {
        // Given
        val field = namedField("my_int", "int")
        val fixed = DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder()
            .setValue("1234")
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.fixed(field, fixed)

        // Then
        result shouldBe DSL.`val`(1234)
    }

    @Test
    fun `fixed string with int field should throw exception`() {
        // Given
        val field = namedField("my_int", "int")
        val fixed = DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder()
            .setValue("foo")
            .build()

        // Then
        shouldThrow<BadRequestException> {
            DefaultProcessingPlatformTransformer.fixed(field, fixed)
        }.apply {
            message shouldBe """com.github.drapostolos.typeparser.TypeParserException: 
	Can not parse "foo" {preprocessed: "foo"} to type "java.lang.Integer" {instance of: java.lang.Class} 
	due to: NumberFormatException For input string: "foo""""
            badRequest.fieldViolationsList shouldContainExactly listOf(
                BadRequest.FieldViolation.newBuilder()
                    .setField("dataPolicy.ruleSetsList.fieldTransformsList.fixed")
                    .setDescription("Data type of fixed value provided for field my_int does not match the data type of the field")
                    .build()
            )
        }
    }

    @Test
    fun `hash with a seed`() {
        // Given
        val field = namedField("my_field")
        val hash = DataPolicy.RuleSet.FieldTransform.Transform.Hash.newBuilder()
            .setSeed(1234)
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.hash(field, hash)

        // Then
        result.toSql() shouldBe "hash(1234, my_field)"
    }

    @Test
    fun `hash without a seed`() {
        // Given
        val field = namedField("my_field")
        val hash = DataPolicy.RuleSet.FieldTransform.Transform.Hash.getDefaultInstance()

        // When
        val result = DefaultProcessingPlatformTransformer.hash(field, hash)

        // Then
        result.toSql() shouldBe "hash(my_field)"
    }

    @Test
    fun `valid SQL statement transform`() {
        // Given
        val sqlStatement = DataPolicy.RuleSet.FieldTransform.Transform.SqlStatement.newBuilder()
            .setStatement("case when my_field = 'foo' then 'bar' else 'baz' end")
            .build()
        val parser = DSL.using(SQLDialect.DEFAULT).parser()

        // When
        val result = DefaultProcessingPlatformTransformer.sqlStatement(parser, sqlStatement)

        // Then
        result.toSql() shouldBe "case when my_field = 'foo' then 'bar' else 'baz' end"
    }

    @Test
    fun `invalid SQL statement transform`() {
        // Given
        val sqlStatement = DataPolicy.RuleSet.FieldTransform.Transform.SqlStatement.newBuilder()
            .setStatement("' OR 1=1")
            .build()

        // Then
        shouldThrow<BadRequestException> {
            DefaultProcessingPlatformTransformer.sqlStatement(DSL.using(SQLDialect.DEFAULT).parser(), sqlStatement)
        }.apply {
            message shouldBe "org.jooq.impl.ParserException: String literal not terminated: [1:2] '[*] OR 1=1"
            badRequest.fieldViolationsList shouldContainExactly listOf(
                BadRequest.FieldViolation.newBuilder()
                    .setField("dataPolicy.ruleSetsList.fieldTransformsList.sqlStatement")
                    .setDescription("Error parsing SQL statement: String literal not terminated: [1:2] \'[*] OR 1=1")
                    .build()
            )
        }
    }

    @Test
    fun nullify() {
        // When
        val result = DefaultProcessingPlatformTransformer.nullify()

        // Then
        result shouldBe DSL.inline<Any>(null)
    }

    @Test
    fun identity() {
        // Given
        val field = namedField("my_field")

        // When
        val result = DefaultProcessingPlatformTransformer.identity(field)

        // Then
        result.toSql() shouldBe "my_field"
    }

    @Test
    fun detokenize() {
        // Given
        val tokenizedField = namedField("tokenized_field")
        val detokenize = DataPolicy.RuleSet.FieldTransform.Transform.Detokenize.newBuilder()
            .setTokenField(namedField("token_field"))
            .setValueField(namedField("value_field"))
            .setTokenSourceRef("my_token_schema.my_tokens")
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.detokenize(tokenizedField, detokenize, "my_schema.my_table")

        // Then
        result.toSql() shouldBe "coalesce(my_token_schema.my_tokens.value_field, my_schema.my_table.tokenized_field)"
    }

    @Test
    fun `numeric rounding - ceil`() {
        // Given
        val field = namedField("transactionamount", "integer")
        val ceil = DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.newBuilder()
            .setCeil(
                DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Ceil.newBuilder().setDivisor(200f)
            ).build()

        // When
        val result = DefaultProcessingPlatformTransformer.numericRounding(field, ceil)

        // Then
        result.toSql() shouldBe "(ceil((transactionamount / 2E2)) * 2E2)"
    }

    @Test
    fun `numeric rounding - floor`() {
        // Given
        val field = namedField("transactionamount", "integer")
        val ceil = DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.newBuilder()
            .setFloor(
                DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Floor.newBuilder().setDivisor(-200f)
            ).build()

        // When
        val result = DefaultProcessingPlatformTransformer.numericRounding(field, ceil)

        // Then
        result.toSql() shouldBe "(floor((transactionamount / -2E2)) * -2E2)"
    }

    @Test
    fun `numeric rounding - round without divisor`() {
        // Given
        val field = namedField("transactionamount", "integer")
        val round = DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.newBuilder()
            .setRound(
                DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Round.newBuilder().setPrecision(-1)
            ).build()

        // When
        val result = DefaultProcessingPlatformTransformer.numericRounding(field, round)

        // Then
        result.toSql() shouldBe "round(transactionamount, -1)"
    }

    @Test
    fun `numeric rounding - round with divisor`() {
        // Given
        val field = namedField("transactionamount", "integer")
        val round = DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.newBuilder()
            .setRound(
                DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Round.newBuilder().setPrecision(0).setDivisor(5f)
            ).build()

        // When
        val result = DefaultProcessingPlatformTransformer.numericRounding(field, round)

        // Then
        result.toSql() shouldBe "(round((transactionamount / 5E0), 0) * 5E0)"
    }

    @Test
    fun `aggregation - sum no partition by`() {
        // Given
        val sumField = namedField("transactionamount", "integer")
        val sumAggregation = Aggregation.newBuilder()
            .setSum(Aggregation.Sum.getDefaultInstance())
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.aggregation(sumField, sumAggregation)

        // Then
        result.toSql() shouldBe "sum(transactionamount) over()"
    }

    @Test
    fun `aggregation - sum`() {
        // Given
        val sumField = namedField("transactionamount", "integer")
        val partitionByFields = listOf(
            namedField("brand", "varchar"),
            namedField("age", "integer")
        )
        val sumAggregation = Aggregation.newBuilder()
            .setSum(Aggregation.Sum.getDefaultInstance())
            .addAllPartitionBy(partitionByFields)
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.aggregation(sumField, sumAggregation)

        // Then
        result.toSql() shouldBe "sum(transactionamount) over(partition by brand, age)"
    }

    @Test
    fun `aggregation - avg defaults`() {
        // Given
        val avgField = namedField("transactionamount", "integer")
        val partitionByFields = listOf(
            namedField("brand", "varchar"),
            namedField("age", "integer")
        )
        val avgAggregation = Aggregation.newBuilder()
            .addAllPartitionBy(partitionByFields)
            .setAvg(Aggregation.Avg.getDefaultInstance())
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.aggregation(avgField, avgAggregation)

        // Then
        result.toSql() shouldBe "avg(cast(transactionamount as decimal)) over(partition by brand, age)"
    }

    @Test
    fun `aggregation - avg with precision and type cast`() {
        // Given
        val avgField = namedField("transactionamount", "integer")
        val partitionByFields = listOf(
            namedField("brand", "varchar"),
            namedField("age", "integer")
        )
        val avgAggregation = Aggregation.newBuilder()
            .addAllPartitionBy(partitionByFields)
            .setAvg(Aggregation.Avg.newBuilder().setCastTo("float64").setPrecision(2))
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.aggregation(avgField, avgAggregation)

        // Then
        result.toSql() shouldBe "round(avg(cast(transactionamount as float64)) over(partition by brand, age), 2)"
    }

    @Test
    fun `aggregation - avg with 0 precision`() {
        // Given
        val avgField = namedField("transactionamount", "integer")
        val partitionByFields = listOf(
            namedField("brand", "varchar"),
            namedField("age", "integer")
        )
        val avgAggregation = Aggregation.newBuilder()
            .addAllPartitionBy(partitionByFields)
            .setAvg(Aggregation.Avg.newBuilder().setPrecision(0))
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.aggregation(avgField, avgAggregation)

        // Then
        result.toSql() shouldBe "round(avg(cast(transactionamount as decimal)) over(partition by brand, age), 0)"
    }

    @Test
    fun `aggregation - min`() {
        // Given
        val minField = namedField("transactionamount", "integer")
        val partitionByFields = listOf(
            namedField("brand", "varchar"),
            namedField("age", "integer")
        )
        val minAggregation = Aggregation.newBuilder()
            .setMin(Aggregation.Min.getDefaultInstance())
            .addAllPartitionBy(partitionByFields)
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.aggregation(minField, minAggregation)

        // Then
        result.toSql() shouldBe "min(transactionamount) over(partition by brand, age)"
    }

    @Test
    fun `aggregation - max`() {
        // Given
        val maxField = namedField("transactionamount", "integer")
        val partitionByFields = listOf(
            namedField("brand", "varchar"),
            namedField("age", "integer")
        )
        val maxAggregation = Aggregation.newBuilder()
            .setMax(Aggregation.Max.getDefaultInstance())
            .addAllPartitionBy(partitionByFields)
            .build()

        // When
        val result = DefaultProcessingPlatformTransformer.aggregation(maxField, maxAggregation)

        // Then
        result.toSql() shouldBe "max(transactionamount) over(partition by brand, age)"
    }
}
