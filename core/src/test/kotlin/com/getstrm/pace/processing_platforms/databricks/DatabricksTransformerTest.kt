package com.getstrm.pace.processing_platforms.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Hash
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.namedField
import com.getstrm.pace.toSql
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DatabricksTransformerTest {

    private val underTest = DatabricksTransformer

    @Test
    fun `hash - string`() {
        // Given
        val field = namedField("email", "string")
        val hash = Hash.getDefaultInstance()

        // When
        val result = underTest.hash(field, hash)

        // Then
        result.toSql() shouldBe "sha2(cast(email as string), 256)"
    }

    @Test
    fun `hash - numerical`() {
        // Given
        val field = namedField("transactionamount", "integer")
        val hash = Hash.getDefaultInstance()

        // When
        val result = underTest.hash(field, hash)

        // Then
        result.toSql() shouldBe "hash(transactionamount)"
    }

    @Test
    fun `hash - not valid`() {
        // Given
        val field = namedField("timestamp", "timestamp")
        val hash = Hash.getDefaultInstance()

        // Then
        shouldThrow<InternalException> {  underTest.hash(field, hash) }
    }
}
