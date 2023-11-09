package com.getstrm.pace.service

import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.util.parseDataPolicy
import com.getstrm.pace.util.yaml2json
import com.google.rpc.BadRequest
import io.grpc.Status
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class DataPolicyValidatorServiceTest {
    private val underTest: DataPolicyValidatorService = DataPolicyValidatorService()

    @Test
    fun `validate complex happy flow`() {
        @Language("yaml")
        val dataPolicy = """
$policyBase
rule_sets: 
- target:
    type: DYNAMIC_VIEW
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - field:
        name_parts: [ email ]
      transforms:
        - principals:
            - group: analytics
            - group: marketing
          regexp:
            regexp: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - group: fraud-and-risk
            - group: admin
          identity: {}
        - principals: []
          fixed:
            value: "'****'"
    - field:
        name_parts: [ userId ]
      transforms:
        - principals:
            - group: fraud-and-risk
          identity: {}
        - principals: []
          hash:
            seed: "1234"
    - field:
        name_parts: [ items ]
      transforms:
        - principals: []
          fixed:
            value: "'****'"
    - field:
        name_parts: [ brand ]
      transforms:
        - principals: []
          sql_statement:
            statement: "case when brand = 'Macbook' then 'Apple' else 'Other' end"
  filters:
    - field:
        name_parts: [ age ]
      conditions:
        - principals:
            - group: fraud-and-risk
          condition: "true"
        - principals: []
          condition: "age > 18"
    - field:
        name_parts: [ userId ]
      conditions:
        - principals:
            - group: marketing
          condition: "userId in ('1', '2', '3', '4')"
        - principals: []
          condition: "true"
    - field:
        name_parts: [ transactionAmount ]
      conditions:
        - principals: []
          condition: "transactionAmount < 10"
          """.yaml2json().parseDataPolicy()

        assertDoesNotThrow {
            underTest.validate(dataPolicy, setOf("analytics", "marketing", "fraud-and-risk", "admin"))
        }
    }

    @Test
    fun `validate non-empty last`() {
        @Language("yaml")
        val dataPolicy = """
$policyBase
rule_sets: 
- target:
    type: DYNAMIC_VIEW
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - field:
        name_parts: [ email ]
      transforms:
        - principals:
          - group: analytics
          - group: marketing
          regexp:
            regexp: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
          - group: fraud-and-risk
          - group: admin
          identity: {}
        - principals:
          - group: analytics # this triggers the validation error
          fixed:
            value: "'****'"
    - field:
        name_parts: [ userId ]
      transforms:
        - principals:
          - group: fraud-and-risk
          identity: {}
        - principals: []
          hash:
            seed: "1234"
    - field:
        name_parts: [ items ]
      transforms:
        - principals: []
          fixed:
            value: "'****'"
    - field:
        name_parts: [ brand ]
      transforms:
        - principals: []
          sql_statement:
            statement: "case when brand = 'Macbook' then 'Apple' else 'Other' end"
  filters:
    - field:
        name_parts: [ age ]
      conditions:
        - principals:
          - group: fraud-and-risk
          condition: "true"
        - principals: []
          condition: "age > 18"
    - field:
        name_parts: [ userId ]
      conditions:
        - principals:
          - group: marketing
          condition: "userId in ('1', '2', '3', '4')"
        - principals: []
          condition: "true"
    - field:
        name_parts: [ transactionAmount ]
      conditions:
        - principals: []
          condition: "transactionAmount < 10"
          """.yaml2json().parseDataPolicy()

        val exception = shouldThrow<BadRequestException> {
            underTest.validate(dataPolicy, setOf("analytics", "marketing", "fraud-and-risk", "admin"))
        }

        exception.code.status shouldBe Status.INVALID_ARGUMENT
        exception.badRequest.fieldViolationsCount shouldBe 1
        exception.badRequest.fieldViolationsList.first() shouldBe BadRequest.FieldViolation.newBuilder()
            .setField("fieldTransform")
            .setDescription("FieldTransform email does not have an empty principals list as last field")
            .build()
    }

    @Test
    fun `validate missing group`() {
        @Language("yaml")
        val dataPolicy = """
$policyBase
rule_sets: 
- target:
    type: DYNAMIC_VIEW
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - field:
        name_parts:
          - email
      transforms:
        - principals:
            - group: analytics
            - group: marketing
          regexp:
            regexp: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - group: fraud-and-risk
            - group: admin
          identity: {}
        - principals: []
          fixed:
            value: "'****'"
    - field:
        name_parts:
          - userId
      transforms:
        - principals:
            - group: fraud-and-risk
          identity: {}
        - principals: []
          hash:
            seed: "1234"
    - field:
        name_parts:
          - items
      transforms:
        - principals: []
          fixed:
            value: "'****'"
    - field:
        name_parts:
          - brand
      transforms:
        - principals: []
          sql_statement:
            statement: "case when brand = 'Macbook' then 'Apple' else 'Other' end"
  filters:
    - field:
        name_parts: [ age ]
      conditions:
        - principals:
            - group: fraud-and-risk
          condition: "true"
        - principals: []
          condition: "age > 18"
    - field:
        name_parts: [ userId ]
      conditions:
        - principals:
            - group: marketing
          condition: "userId in ('1', '2', '3', '4')"
        - principals: []
          condition: "true"
    - field:
        name_parts: [ transactionAmount ]
      conditions:
        - principals: []
          condition: "transactionAmount < 10"
          """.yaml2json().parseDataPolicy()

        val exception = shouldThrow<BadRequestException> {
            underTest.validate(dataPolicy, setOf("marketing", "fraud-and-risk", "admin"))
        }
        exception.code.status shouldBe Status.INVALID_ARGUMENT
        exception.badRequest.fieldViolationsCount shouldBe 1
        exception.badRequest.fieldViolationsList.first() shouldBe BadRequest.FieldViolation.newBuilder()
            .setField("principal")
            .setDescription("Principal analytics does not exist in platform snowflake")
            .build()
    }

    @Test
    fun `validate overlapping principals`() {
        @Language("yaml")
        val dataPolicy = """
$policyBase
rule_sets: 
- target:
    type: DYNAMIC_VIEW
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - field:
        name_parts: [ email ]
      transforms:
        - principals:
            - group: analytics
            - group: marketing
          regexp:
            regexp: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - group: analytics
          nullify: {}
        - principals:
            - group: fraud-and-risk
            - group: admin
          identity: {}
        - principals: []
          fixed:
            value: "'****'"
    - field:
        name_parts: [ userId ]
      transforms:
        - principals:
            - group: fraud-and-risk
          identity: {}
        - principals: []
          hash:
            seed: "1234"
    - field:
        name_parts: [ items ]
      transforms:
        - principals: []
          fixed:
            value: "'****'"
    - field:
        name_parts: [ brand ]
      transforms:
        - principals: []
          sql_statement:
            statement: "case when brand = 'Macbook' then 'Apple' else 'Other' end"
  filters:
    - field:
        name_parts: [ age ]
      conditions:
        - principals:
            - group: fraud-and-risk
          condition: "true"
        - principals: []
          condition: "age > 18"
    - field:
        name_parts: [ userId ]
      conditions:
        - principals:
            - group: marketing
          condition: "userId in ('1', '2', '3', '4')"
        - principals: []
          condition: "true"
    - field:
        name_parts: [ transactionAmount ]
      conditions:
        - principals: []
          condition: "transactionAmount < 10"
          """.yaml2json().parseDataPolicy()

        val exception = shouldThrow<BadRequestException> {
            underTest.validate(dataPolicy, setOf("analytics", "marketing", "fraud-and-risk", "admin"))
        }
        exception.code.status shouldBe Status.INVALID_ARGUMENT
        exception.badRequest.fieldViolationsCount shouldBe 1
        exception.badRequest.fieldViolationsList.first() shouldBe BadRequest.FieldViolation.newBuilder()
            .setField("fieldTransform")
            .setDescription("FieldTransform email has overlapping principals")
            .build()
    }

    @Test
    fun `validate overlapping fields`() {
        @Language("yaml")
        val dataPolicy = """
$policyBase
rule_sets: 
- target:
    type: DYNAMIC_VIEW
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - field:
        name_parts: [ email ]
      transforms:
        - principals:
            - group: analytics
            - group: marketing
          regexp:
            regexp: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - group: fraud-and-risk
            - group: admin
          identity: {}
        - principals: []
          fixed:
            value: "'****'"
    - field:
        name_parts: [ email ]
      transforms:
        - principals:
            - group: fraud-and-risk
          identity: {}
        - principals: []
          hash:
            seed: "1234"
    - field:
        name_parts: [ items ]
      transforms:
        - principals: []
          fixed:
            value: "'****'"
    - field:
        name_parts: [ brand ]
      transforms:
        - principals: []
          sql_statement:
            statement: "case when brand = 'Macbook' then 'Apple' else 'Other' end"
  filters:
    - field:
        name_parts: [ age ]
      conditions:
        - principals:
            - group: fraud-and-risk
          condition: "true"
        - principals: []
          condition: "age > 18"
    - field:
        name_parts: [ userId ]
      conditions:
        - principals:
            - group: marketing
          condition: "userId in ('1', '2', '3', '4')"
        - principals: []
          condition: "true"
    - field:
        name_parts: [ transactionAmount ]
      conditions:
        - principals: []
          condition: "transactionAmount < 10"
          """.yaml2json().parseDataPolicy()

        val exception = shouldThrow<BadRequestException> {
            underTest.validate(dataPolicy, setOf("analytics", "marketing", "fraud-and-risk", "admin"))
        }
        exception.code.status shouldBe Status.INVALID_ARGUMENT
        exception.badRequest.fieldViolationsCount shouldBe 1
        exception.badRequest.fieldViolationsList.first() shouldBe BadRequest.FieldViolation.newBuilder()
            .setField("ruleSet")
            .setDescription("RuleSet has overlapping fields, email is already present")
            .build()
    }
}

// convenience for tests
fun groups(vararg group: String) = group.map { Group(it, it, it) }

/*
 * base yaml of policy with happy flow attributes
 */
const val policyBase = """
platform:
  platform_type: SNOWFLAKE
  id: snowflake
source: 
  type: SQL_DDL
  ref: mycatalog.my_schema.gddemo
  fields:
    - name_parts: [transactionId]
      type: bigint
    - name_parts: [userId]
      type: string
    - name_parts: [email]
      type: string
    - name_parts: [age]
      type: bigint h
    - name_parts: [size]
      type: string
    - name_parts: [brand]
      type: string
    - name_parts: [transactionAmount]
      type: bigint
    - name_parts: [items]
      type: string
    - name_parts: [itemCount]
      type: bigint
    - name_parts: [date]
      type: timestamp
    - name_parts: [purpose]
      type: bigint
    
"""