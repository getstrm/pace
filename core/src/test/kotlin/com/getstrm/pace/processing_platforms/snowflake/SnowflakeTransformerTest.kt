package com.getstrm.pace.processing_platforms.snowflake

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.namedField
import com.getstrm.pace.toSql
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SnowflakeTransformerTest {

    private val underTest = SnowflakeTransformer

    @Test
    fun `regexp extract should use regexp_substr`() {
        // Given
        val field = namedField("my_field")
        val regexp =
            DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder()
                .setRegexp("@\\w+\\.com$")
                .build()

        // When
        val result = underTest.regexpReplace(field, regexp)

        // Then
        result.toSql() shouldBe "regexp_substr(my_field, '@\\w+\\.com$')"
    }

    @Test
    fun `regexp replace should use double backslash notation for capturing group backreferences`() {
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
            """regexp_replace(my_field, 'foo(\w+) bar(\w+)', 'fizz\\1buzz\\2baz$')"""
    }

    @Test
    fun `hash - string`() {
        // Given
        val field = namedField("email", "string")
        val hash = DataPolicy.RuleSet.FieldTransform.Transform.Hash.getDefaultInstance()

        // When
        val result = underTest.hash(field, hash)

        // Then
        result.toSql() shouldBe "cast(hash(email) as varchar)"
    }

    @Test
    fun `hash - numerical`() {
        // Given
        val field = namedField("transactionamount", "integer")
        val hash = DataPolicy.RuleSet.FieldTransform.Transform.Hash.getDefaultInstance()

        // When
        val result = underTest.hash(field, hash)

        // Then
        result.toSql() shouldBe "hash(transactionamount)"
    }

    @Test
    fun `hash - not valid`() {
        // Given
        val field = namedField("timestamp", "timestamp")
        val hash = DataPolicy.RuleSet.FieldTransform.Transform.Hash.getDefaultInstance()

        // Then
        shouldThrow<InternalException> {  underTest.hash(field, hash) }
    }
}
