package com.getstrm.pace.common

import com.getstrm.pace.TestDynamicViewGenerator
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.BadRequestException
import com.google.rpc.BadRequest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AbstractDynamicViewGeneratorTest {
    private val underTest = TestDynamicViewGenerator(DataPolicy.getDefaultInstance())

    @Test
    fun `case - sql statement - invalid`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setSqlStatement(
                DataPolicy.RuleSet.FieldTransform.Transform.SqlStatement.newBuilder()
                    .setStatement("some invalid sql statement")
            )
            .build()

        // When
        val exception = assertThrows<BadRequestException> { underTest.toCase(transform, attribute) }

        // Then
        exception.badRequest.fieldViolationsCount shouldBe 1
        exception.badRequest.fieldViolationsList.first() shouldBe BadRequest.FieldViolation.newBuilder()
            .setField("dataPolicy.ruleSetsList.fieldTransformsList.sqlStatement")
            .setDescription("Error parsing SQL statement: Unexpected content after end of field input: [1:6] some [*]invalid sql statement")
            .build()
    }
}
