package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.util.toProto
import com.google.rpc.BadRequest
import io.grpc.Status
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class DataPolicyValidatorServiceTest {
    private val underTest: DataPolicyValidatorService = DataPolicyValidatorService()

    @Test
    fun `validate complex happy flow`() {
        @Language("yaml")
        val dataPolicy =
            """
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
            statement: "case when brand = 'MacBook' then 'Apple' else 'Other' end"
  filters:
    - generic_filter:
        field:
          name_parts: [ age ]
        conditions:
          - principals:
              - group: fraud-and-risk
            condition: "true"
          - principals: []
            condition: "age > 18"
    - generic_filter:
        field:
          name_parts: [ userId ]
        conditions:
          - principals:
              - group: marketing
            condition: "userId in ('1', '2', '3', '4')"
          - principals: []
            condition: "true"
    - generic_filter:
        field:
          name_parts: [ transactionAmount ]
        conditions:
          - principals: []
            condition: "transactionAmount < 10"
        """
                .toProto<DataPolicy>()

        assertDoesNotThrow {
            underTest.validate(
                dataPolicy,
                setOf("analytics", "marketing", "fraud-and-risk", "admin")
            )
        }
    }

    @Test
    fun `validate non-empty last`() {
        @Language("yaml")
        val dataPolicy =
            """
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
            statement: "case when brand = 'MacBook' then 'Apple' else 'Other' end"
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
          """
                .toProto<DataPolicy>(false)

        val exception =
            shouldThrow<BadRequestException> {
                underTest.validate(
                    dataPolicy,
                    setOf("analytics", "marketing", "fraud-and-risk", "admin")
                )
            }

        exception.code.status shouldBe Status.INVALID_ARGUMENT
        exception.badRequest.fieldViolationsCount shouldBe 1
        exception.badRequest.fieldViolationsList.first() shouldBe
            BadRequest.FieldViolation.newBuilder()
                .setField("fieldTransform")
                .setDescription(
                    "FieldTransform email does not have an empty principals list as last field"
                )
                .build()
    }

    @Test
    fun `validate missing group`() {
        @Language("yaml")
        val dataPolicy =
            """
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
            statement: "case when brand = 'MacBook' then 'Apple' else 'Other' end"
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
          """
                .toProto<DataPolicy>(false)

        val exception =
            shouldThrow<BadRequestException> {
                underTest.validate(dataPolicy, setOf("marketing", "fraud-and-risk", "admin"))
            }
        exception.code.status shouldBe Status.INVALID_ARGUMENT
        exception.badRequest.fieldViolationsCount shouldBe 1
        exception.badRequest.fieldViolationsList.first() shouldBe
            BadRequest.FieldViolation.newBuilder()
                .setField("principal")
                .setDescription("Principal analytics does not exist in platform snowflake")
                .build()
    }

    @Test
    fun `validate overlapping principals`() {
        @Language("yaml")
        val dataPolicy =
            """
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
            statement: "case when brand = 'MacBook' then 'Apple' else 'Other' end"
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
          """
                .toProto<DataPolicy>(false)

        val exception =
            shouldThrow<BadRequestException> {
                underTest.validate(
                    dataPolicy,
                    setOf("analytics", "marketing", "fraud-and-risk", "admin")
                )
            }
        exception.code.status shouldBe Status.INVALID_ARGUMENT
        exception.badRequest.fieldViolationsCount shouldBe 1
        exception.badRequest.fieldViolationsList.first() shouldBe
            BadRequest.FieldViolation.newBuilder()
                .setField("fieldTransform")
                .setDescription("FieldTransform email has overlapping principals")
                .build()
    }

    @Test
    fun `validate overlapping fields`() {
        @Language("yaml")
        val dataPolicy =
            """
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
            statement: "case when brand = 'MacBook' then 'Apple' else 'Other' end"
  filters:
    - generic_filter:
        field:
          name_parts: [ age ]
        conditions:
          - principals:
              - group: fraud-and-risk
            condition: "true"
          - principals: []
            condition: "age > 18"
    - generic_filter:
        field:
          name_parts: [ userId ]
        conditions:
          - principals:
              - group: marketing
            condition: "userId in ('1', '2', '3', '4')"
          - principals: []
            condition: "true"
    - generic_filter:
        field:
          name_parts: [ transactionAmount ]
        conditions:
          - principals: []
            condition: "transactionAmount < 10"
          """
                .toProto<DataPolicy>()

        val exception =
            shouldThrow<BadRequestException> {
                underTest.validate(
                    dataPolicy,
                    setOf("analytics", "marketing", "fraud-and-risk", "admin")
                )
            }
        exception.code.status shouldBe Status.INVALID_ARGUMENT
        exception.badRequest.fieldViolationsCount shouldBe 1
        exception.badRequest.fieldViolationsList.first() shouldBe
            BadRequest.FieldViolation.newBuilder()
                .setField("ruleSet")
                .setDescription("RuleSet has overlapping fields, email is already present")
                .build()
    }

    @Test
    fun `validate duplicate token source refs`() {
        @Language("yaml")
        val dataPolicy =
            """
                metadata:
                  description: ""
                  version: 1
                  title: public.demo
                platform:
                  id: platform-id
                  platform_type: POSTGRES
                source:
                  fields:
                    - name_parts:
                        - transactionid
                      required: true
                      type: integer
                    - name_parts:
                        - userid
                      required: true
                      type: integer
                    - name_parts:
                        - transactionamount
                      required: true
                      type: integer
                  ref: public.demo_tokenized
                rule_sets:
                  - target:
                      fullname: public.demo_view
                    filters:
                      - conditions:
                          - principals: [ {group: fraud_and_risk} ]
                            condition: "true"
                          - principals : []
                            condition: "transactionamount < 10"
                    field_transforms:
                      - field:
                          name_parts: [ userid ]
                        transforms:
                          - principals: [ {group: fraud_and_risk} ]
                            detokenize:
                              token_source_ref: tokens.all_tokens
                              token_field:
                                name_parts: [ token ]
                              value_field:
                                name_parts: [ value ]
                          - principals: []
                            identity: {}
                      - field:
                          name_parts: [ transactionid ]
                        transforms:
                          - principals: [ {group: fraud_and_risk} ]
                            detokenize:
                              token_source_ref: tokens.all_tokens
                              token_field:
                                name_parts: [ token ]
                              value_field:
                                name_parts: [ value ]
                          - principals: []
                            identity: {}
    
            """
                .trimIndent()
                .toProto<DataPolicy>(false)
        runBlocking {
            val exception =
                shouldThrow<BadRequestException> {
                    underTest.validate(
                        dataPolicy,
                        setOf("analytics", "marketing", "fraud-and-risk", "admin")
                    )
                }
            exception.code.status shouldBe Status.INVALID_ARGUMENT
            exception.badRequest.fieldViolationsCount shouldBe 1
            exception.badRequest.fieldViolationsList.first() shouldBe
                BadRequest.FieldViolation.newBuilder()
                    .setField("ruleSet")
                    .setDescription(
                        "RuleSet has duplicate token sources: [tokens.all_tokens, tokens.all_tokens]. Each Detokenize transform must have a unique token source."
                    )
                    .build()
        }
    }
}

/*
 * base yaml of policy with happy flow attributes
 */
const val policyBase =
    """
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
