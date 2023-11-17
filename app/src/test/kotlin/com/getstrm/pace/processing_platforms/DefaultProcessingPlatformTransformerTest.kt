package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
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
    fun `numeric rounding - round`() {
        // Given
        val field = namedField("transactionamount", "integer")
        val ceil = DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.newBuilder()
            .setRound(
                DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Round.newBuilder().setPrecision(-1)
            ).build()

        // When
        val result = DefaultProcessingPlatformTransformer.numericRounding(field, ceil)

        // Then
        result.toSql() shouldBe "round(transactionamount, -1)"
    }
}