package com.getstrm.pace.service

import com.getstrm.pace.dao.DataPolicyDao
import com.getstrm.pace.domain.Group
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.snowflake.SnowflakeClient
import com.google.rpc.BadRequest
import com.google.rpc.ResourceInfo
import io.grpc.Status
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.getstrm.pace.util.parseDataPolicy
import com.getstrm.pace.util.yaml2json

class DataPolicyServiceTest {

    private lateinit var underTest: DataPolicyService
    private val defaultContext = "default"
    private val dao = mockk<DataPolicyDao>()
    private val platforms = mockk<ProcessingPlatformsService>()
    private val jooq = mockk<DSLContext>()
    private val platform = mockk<SnowflakeClient>()

    @BeforeEach
    fun setUp() {
        underTest = DataPolicyService(defaultContext, dao, platforms, jooq)
    }

    @Test
    fun `validate complex happy flow`() {
        val dataPolicy = """
$policyBase
rule_sets: 
- target:
    type: DYNAMIC_VIEW
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - attribute:
        path_components: [ email ]
      transforms:
        - principals:
            - analytics
            - marketing
          regex:
            regex: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - fraud-detection
            - admin
          identity: true
        - principals: []
          fixed:
            value: "'****'"
    - attribute:
        path_components: [ userId ]
      transforms:
        - principals:
            - fraud-detection
          identity: true
        - principals: []
          hash:
            seed: "1234"
    - attribute:
        path_components: [ items ]
      transforms:
        - principals: []
          fixed:
            value: "'****'"
    - attribute:
        path_components: [ hairColor ]
      transforms:
        - principals: []
          sql_statement:
            statement: "case when hairColor = 'blonde' then 'fair' else 'dark' end"
  filters:
    - attribute:
        path_components: [ age ]
      conditions:
        - principals:
            - fraud-detection
          condition: "true"
        - principals: []
          condition: "age > 18"
    - attribute:
        path_components: [ userId ]
      conditions:
        - principals:
            - marketing
          condition: "userId in ('1', '2', '3', '4')"
        - principals: []
          condition: "true"
    - attribute:
        path_components: [ transactionAmount ]
      conditions:
        - principals: []
          condition: "transactionAmount < 10"
          """.yaml2json().parseDataPolicy()
        coEvery { platforms.getProcessingPlatform(dataPolicy) } returns platform
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-detection", "admin")
        runBlocking {
            underTest.validate(dataPolicy)
        }
    }

    @Test
    fun `validate processing platform`() {
        val dataPolicy = """
$policyBase
rule_sets: 
- target:
    type: DYNAMIC_VIEW
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - attribute:
        path_components: [ email ]
      transforms:
        - principals:
            - analytics
            - marketing
          regex:
            regex: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - fraud-detection
            - admin
          identity: true
        - principals: [analytics]
          fixed:
            value: "'****'"
    - attribute:
        path_components: [ userId ]
      transforms:
        - principals:
            - fraud-detection
          identity: true
        - principals: []
          hash:
            seed: "1234"
    - attribute:
        path_components: [ items ]
      transforms:
        - principals: []
          fixed:
            value: "'****'"
    - attribute:
        path_components: [ hairColor ]
      transforms:
        - principals: []
          sql_statement:
            statement: "case when hairColor = 'blonde' then 'fair' else 'dark' end"
  filters:
    - attribute:
        path_components: [ age ]
      conditions:
        - principals:
            - fraud-detection
          condition: "true"
        - principals: []
          condition: "age > 18"
    - attribute:
        path_components: [ userId ]
      conditions:
        - principals:
            - marketing
          condition: "userId in ('1', '2', '3', '4')"
        - principals: []
          condition: "true"
    - attribute:
        path_components: [ transactionAmount ]
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
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-detection", "admin")
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
        val dataPolicy = """
$policyBase
rule_sets: 
- target:
    type: DYNAMIC_VIEW
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - attribute:
        path_components: [ email ]
      transforms:
        - principals:
            - analytics
            - marketing
          regex:
            regex: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - fraud-detection
            - admin
          identity: true
        - principals: [analytics] # this triggers the validation error
          fixed:
            value: "'****'"
    - attribute:
        path_components: [ userId ]
      transforms:
        - principals:
            - fraud-detection
          identity: true
        - principals: []
          hash:
            seed: "1234"
    - attribute:
        path_components: [ items ]
      transforms:
        - principals: []
          fixed:
            value: "'****'"
    - attribute:
        path_components: [ hairColor ]
      transforms:
        - principals: []
          sql_statement:
            statement: "case when hairColor = 'blonde' then 'fair' else 'dark' end"
  filters:
    - attribute:
        path_components: [ age ]
      conditions:
        - principals:
            - fraud-detection
          condition: "true"
        - principals: []
          condition: "age > 18"
    - attribute:
        path_components: [ userId ]
      conditions:
        - principals:
            - marketing
          condition: "userId in ('1', '2', '3', '4')"
        - principals: []
          condition: "true"
    - attribute:
        path_components: [ transactionAmount ]
      conditions:
        - principals: []
          condition: "transactionAmount < 10"
          """.yaml2json().parseDataPolicy()
        coEvery { platforms.getProcessingPlatform(dataPolicy) } returns platform
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-detection", "admin")
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
        val dataPolicy = """
$policyBase
rule_sets: 
- target:
    type: DYNAMIC_VIEW
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - attribute:
        path_components:
          - email
      transforms:
        - principals:
            - analytics
            - marketing
          regex:
            regex: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - fraud-detection
            - admin
          identity: true
        - principals: []
          fixed:
            value: "'****'"
    - attribute:
        path_components:
          - userId
      transforms:
        - principals:
            - fraud-detection
          identity: true
        - principals: []
          hash:
            seed: "1234"
    - attribute:
        path_components:
          - items
      transforms:
        - principals: []
          fixed:
            value: "'****'"
    - attribute:
        path_components:
          - hairColor
      transforms:
        - principals: []
          sql_statement:
            statement: "case when hairColor = 'blonde' then 'fair' else 'dark' end"
  filters:
    - attribute:
        path_components: [ age ]
      conditions:
        - principals:
            - fraud-detection
          condition: "true"
        - principals: []
          condition: "age > 18"
    - attribute:
        path_components: [ userId ]
      conditions:
        - principals:
            - marketing
          condition: "userId in ('1', '2', '3', '4')"
        - principals: []
          condition: "true"
    - attribute:
        path_components: [ transactionAmount ]
      conditions:
        - principals: []
          condition: "transactionAmount < 10"
          """.yaml2json().parseDataPolicy()
        coEvery { platforms.getProcessingPlatform(dataPolicy) } returns platform
        coEvery { platform.listGroups() } returns groups("marketing", "fraud-detection", "admin")
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
        val dataPolicy = """
$policyBase
rule_sets: 
- target:
    type: DYNAMIC_VIEW
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - attribute:
        path_components: [ email ]
      transforms:
        - principals:
            - analytics
            - marketing
          regex:
            regex: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - analytics
          nullify: {}
        - principals:
            - fraud-detection
            - admin
          identity: true
        - principals: []
          fixed:
            value: "'****'"
    - attribute:
        path_components: [ userId ]
      transforms:
        - principals:
            - fraud-detection
          identity: true
        - principals: []
          hash:
            seed: "1234"
    - attribute:
        path_components: [ items ]
      transforms:
        - principals: []
          fixed:
            value: "'****'"
    - attribute:
        path_components: [ hairColor ]
      transforms:
        - principals: []
          sql_statement:
            statement: "case when hairColor = 'blonde' then 'fair' else 'dark' end"
  filters:
    - attribute:
        path_components: [ age ]
      conditions:
        - principals:
            - fraud-detection
          condition: "true"
        - principals: []
          condition: "age > 18"
    - attribute:
        path_components: [ userId ]
      conditions:
        - principals:
            - marketing
          condition: "userId in ('1', '2', '3', '4')"
        - principals: []
          condition: "true"
    - attribute:
        path_components: [ transactionAmount ]
      conditions:
        - principals: []
          condition: "transactionAmount < 10"
          """.yaml2json().parseDataPolicy()
        coEvery { platforms.getProcessingPlatform(dataPolicy) } returns platform
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-detection", "admin")
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
    fun `validate overlapping attributes`() {
        val dataPolicy = """
$policyBase
rule_sets: 
- target:
    type: DYNAMIC_VIEW
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - attribute:
        path_components: [ email ]
      transforms:
        - principals:
            - analytics
            - marketing
          regex:
            regex: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - fraud-detection
            - admin
          identity: true
        - principals: []
          fixed:
            value: "'****'"
    - attribute:
        path_components: [ email ]
      transforms:
        - principals:
            - fraud-detection
          identity: true
        - principals: []
          hash:
            seed: "1234"
    - attribute:
        path_components: [ items ]
      transforms:
        - principals: []
          fixed:
            value: "'****'"
    - attribute:
        path_components: [ hairColor ]
      transforms:
        - principals: []
          sql_statement:
            statement: "case when hairColor = 'blonde' then 'fair' else 'dark' end"
  filters:
    - attribute:
        path_components: [ age ]
      conditions:
        - principals:
            - fraud-detection
          condition: "true"
        - principals: []
          condition: "age > 18"
    - attribute:
        path_components: [ userId ]
      conditions:
        - principals:
            - marketing
          condition: "userId in ('1', '2', '3', '4')"
        - principals: []
          condition: "true"
    - attribute:
        path_components: [ transactionAmount ]
      conditions:
        - principals: []
          condition: "transactionAmount < 10"
          """.yaml2json().parseDataPolicy()
        coEvery { platforms.getProcessingPlatform(dataPolicy) } returns platform
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-detection", "admin")
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
  attributes:
    - path_components: [transactionId]
      type: bigint
    - path_components: [userId]
      type: string
    - path_components: [email]
      type: string
    - path_components: [age]
      type: bigint h
    - path_components: [size]
      type: string
    - path_components: [hairColor]
      type: string
    - path_components: [transactionAmount]
      type: bigint
    - path_components: [items]
      type: string
    - path_components: [itemCount]
      type: bigint
    - path_components: [date]
      type: timestamp
    - path_components: [purpose]
      type: bigint
    
"""
