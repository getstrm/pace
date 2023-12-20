package com.getstrm.pace.processing_platforms.h2

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.namedField
import com.getstrm.pace.toSql
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class H2TransformerTest {

    private val underTest = H2Transformer()

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
    fun `regexp replace should keep dollar notation`() {
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
            """regexp_replace(my_field, 'foo(\w+) bar(\w+)', 'fizz$1buzz$2baz$')"""
    }
}
