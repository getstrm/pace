package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.config.AppConfiguration
import com.getstrm.pace.config.PaceConfiguration
import com.getstrm.pace.config.ProcessingPlatformsConfiguration
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
    private val appConfig =
        PaceConfiguration(
            appConfiguration =
                AppConfiguration(processingPlatforms = ProcessingPlatformsConfiguration())
        )
    private val underTest: DataPolicyValidatorService = DataPolicyValidatorService(appConfig)

    @Test
    fun `validate complex happy flow`() {
        @Language("yaml")
        val dataPolicy =
            """
$policyBase
rule_sets: 
- target:
    ref:
      integration_fqn: my_catalog.my_schema.gddemo_public
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
        conditions:
          - principals:
              - group: fraud-and-risk
            condition: "true"
          - principals: []
            condition: "age > 18"
    - generic_filter:
        conditions:
          - principals:
              - group: marketing
            condition: "userId in ('1', '2', '3', '4')"
          - principals: []
            condition: "true"
    - generic_filter:
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
    fun `validate complex happy flow with different cases`() {
        @Language("yaml")
        val dataPolicy =
            """
$policyBase
rule_sets: 
- target:
    ref:
      integration_fqn: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - field:
        name_parts: [ EMAil ]
      transforms:
        - principals:
            - group: anALYTics
            - group: maRKETing
          regexp:
            regexp: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - group: frAUD-AND-Risk
            - group: admin
          identity: {}
        - principals: []
          fixed:
            value: "'****'"
    - field:
        name_parts: [ usERId ]
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
        name_parts: [ brANd ]
      transforms:
        - principals: []
          sql_statement:
            statement: "case when brand = 'MacBook' then 'Apple' else 'Other' end"
  filters:
    - generic_filter:
        conditions:
          - principals:
              - group: fraud-and-risk
            condition: "true"
          - principals: []
            condition: "age > 18"
    - generic_filter:
        conditions:
          - principals:
              - group: marketing
            condition: "userId in ('1', '2', '3', '4')"
          - principals: []
            condition: "true"
    - generic_filter:
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
    ref:
      integration_fqn: my_catalog.my_schema.gddemo_public
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
    - generic_filter:
          conditions:
            - principals:
              - group: fraud-and-risk
              condition: "true"
            - principals: []
              condition: "age > 18"
    - generic_filter:
          conditions:
            - principals:
              - group: marketing
              condition: "userId in ('1', '2', '3', '4')"
            - principals: []
              condition: "true"
    - generic_filter:
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
    ref:
      integration_fqn: my_catalog.my_schema.gddemo_public
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
    - generic_filter:
          conditions:
            - principals:
                - group: fraud-and-risk
              condition: "true"
            - principals: []
              condition: "age > 18"
    - generic_filter:
          conditions:
            - principals:
                - group: marketing
              condition: "userId in ('1', '2', '3', '4')"
            - principals: []
              condition: "true"
    - generic_filter:
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
    ref:
      integration_fqn: my_catalog.my_schema.gddemo_public
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
    - generic_filter:
          conditions:
            - principals:
                - group: fraud-and-risk
              condition: "true"
            - principals: []
              condition: "age > 18"
    - generic_filter:
          conditions:
            - principals:
                - group: marketing
              condition: "userId in ('1', '2', '3', '4')"
            - principals: []
              condition: "true"
    - generic_filter:
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
    ref:
      integration_fqn: my_catalog.my_schema.gddemo_public
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
        conditions:
          - principals:
              - group: fraud-and-risk
            condition: "true"
          - principals: []
            condition: "age > 18"
    - generic_filter:
        conditions:
          - principals:
              - group: marketing
            condition: "userId in ('1', '2', '3', '4')"
          - principals: []
            condition: "true"
    - generic_filter:
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
  ref:
    integration_fqn: public.demo_tokenized
    platform:
      id: platform-id
      platform_type: POSTGRES
rule_sets:
  - target:
      ref: 
        integration_fqn: public.demo_view
    filters:
      - generic_filter: 
          conditions:
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

    @Test
    fun `filters last principal list is not empty`() {
        @Language("yaml")
        val dataPolicy =
            """
$policyBase
rule_sets: 
- target:
    ref:
      integration_fqn: my_catalog.my_schema.gddemo_public
  filters:
    - generic_filter:
        conditions:
          - principals:
              - group: fraud-and-risk
            condition: "true"
          - principals: []
            condition: "age > 18"
    - generic_filter:
        conditions:
          - principals:
              - group: marketing
            condition: "userId in ('1', '2', '3', '4')"
          - principals:
              - group: fraud-and-risk
            condition: "true"
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
                .setField("ruleSet.genericFilter")
                .setDescription("RuleSet.GenericFilter has non-empty last principals list")
                .build()
    }
}

/*
 * base yaml of policy with happy flow attributes
 */
const val policyBase =
    """
source:
  ref:
    integration_fqn: mycatalog.my_schema.gddemo
    platform:
      platform_type: SNOWFLAKE
      id: snowflake
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
