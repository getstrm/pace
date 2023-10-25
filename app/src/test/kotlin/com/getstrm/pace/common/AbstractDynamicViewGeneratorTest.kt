package com.getstrm.pace.common

import com.getstrm.pace.util.TestDynamicViewGenerator
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.pace.domain.SqlParseException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AbstractDynamicViewGeneratorTest {
    private val underTest = TestDynamicViewGenerator(DataPolicy.getDefaultInstance())

    @Test
    fun `case - sql statement - invalid`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setSqlStatement(
                DataPolicy.RuleSet.FieldTransform.Transform.SqlStatement.newBuilder()
                    .setStatement("some invalid sql statement")
            )
            .build()

        // When
        val exception = assertThrows<SqlParseException> { underTest.toCase(transform, attribute) }

        // Then
        exception.message shouldBe "SQL Statement [some invalid sql statement] is invalid, please verify it's syntax. Details: [1:6] some [*]invalid sql statement"
    }
}
