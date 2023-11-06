package com.getstrm.pace.service

import com.getstrm.pace.dao.DataPolicyDao
import com.getstrm.pace.domain.Group
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.snowflake.SnowflakeClient
import com.getstrm.pace.util.parseDataPolicy
import com.getstrm.pace.util.yaml2json
import com.google.rpc.BadRequest
import com.google.rpc.ResourceInfo
import io.grpc.Status
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DataPolicyServiceTest {
    private lateinit var underTest: DataPolicyService
    private val dao = mockk<DataPolicyDao>()
    private val platforms = mockk<ProcessingPlatformsService>()
    private val platform = mockk<SnowflakeClient>()

    @BeforeEach
    fun setUp() {
        underTest = DataPolicyService(dao, platforms)
    }

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
        coEvery { platforms.getProcessingPlatform(dataPolicy) } returns platform
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-and-risk", "admin")
        runBlocking {
            underTest.validate(dataPolicy)
        }
    }

    @Test
    fun `validate processing platform`() {
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
        - principals: [ {group: analytics} ]
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
        coEvery { platforms.getProcessingPlatform(dataPolicy) } throws ResourceException(
            ResourceException.Code.NOT_FOUND,
            ResourceInfo.newBuilder()
                .setResourceType("Processing Platform")
                .setResourceName(dataPolicy.platform.id)
                .setDescription("Platform with id ${dataPolicy.platform.id} not found, please ensure it is present in the configuration of the processing platforms.")
                .setOwner("SNOWFLAKE")
                .build()
        )
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-and-risk", "admin")
        runBlocking {
            val exception = shouldThrow<ResourceException> {
                underTest.validate(dataPolicy)
            }

            exception.code.status shouldBe Status.NOT_FOUND
            exception.resourceInfo shouldBe ResourceInfo.newBuilder()
                .setResourceType("Processing Platform")
                .setResourceName("snowflake")
                .setDescription("Platform with id snowflake not found, please ensure it is present in the configuration of the processing platforms.")
                .setOwner("SNOWFLAKE")
                .build()
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
        coEvery { platforms.getProcessingPlatform(dataPolicy) } returns platform
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-and-risk", "admin")
        runBlocking {
            val exception = shouldThrow<BadRequestException> {
                underTest.validate(dataPolicy)
            }

            exception.code.status shouldBe Status.INVALID_ARGUMENT
            exception.badRequest.fieldViolationsCount shouldBe 1
            exception.badRequest.fieldViolationsList.first() shouldBe BadRequest.FieldViolation.newBuilder()
                .setField("fieldTransform")
                .setDescription("FieldTransform email does not have an empty principals list as last field")
                .build()
        }
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
        coEvery { platforms.getProcessingPlatform(dataPolicy) } returns platform
        coEvery { platform.listGroups() } returns groups("marketing", "fraud-and-risk", "admin")
        runBlocking {
            val exception = shouldThrow<BadRequestException> {
                underTest.validate(dataPolicy)
            }
            exception.code.status shouldBe Status.INVALID_ARGUMENT
            exception.badRequest.fieldViolationsCount shouldBe 1
            exception.badRequest.fieldViolationsList.first() shouldBe BadRequest.FieldViolation.newBuilder()
                .setField("principal")
                .setDescription("Principal analytics does not exist in platform snowflake")
                .build()
        }
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
        coEvery { platforms.getProcessingPlatform(dataPolicy) } returns platform
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-and-risk", "admin")
        runBlocking {
            val exception = shouldThrow<BadRequestException> {
                underTest.validate(dataPolicy)
            }
            exception.code.status shouldBe Status.INVALID_ARGUMENT
            exception.badRequest.fieldViolationsCount shouldBe 1
            exception.badRequest.fieldViolationsList.first() shouldBe BadRequest.FieldViolation.newBuilder()
                .setField("fieldTransform")
                .setDescription("FieldTransform email has overlapping principals")
                .build()
        }
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
        coEvery { platforms.getProcessingPlatform(dataPolicy) } returns platform
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-and-risk", "admin")
        runBlocking {
            val exception = shouldThrow<BadRequestException> {
                underTest.validate(dataPolicy)
            }
            exception.code.status shouldBe Status.INVALID_ARGUMENT
            exception.badRequest.fieldViolationsCount shouldBe 1
            exception.badRequest.fieldViolationsList.first() shouldBe BadRequest.FieldViolation.newBuilder()
                .setField("ruleSet")
                .setDescription("RuleSet has overlapping attributes, email is already present")
                .build()
        }
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
