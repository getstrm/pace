package com.getstrm.pace.service

import com.getstrm.pace.dao.DataPolicyDao
import com.getstrm.pace.domain.Group
import com.getstrm.pace.domain.InvalidDataPolicyMissingAttribute
import com.getstrm.pace.domain.InvalidDataPolicyNonEmptyLastFieldTransform
import com.getstrm.pace.domain.InvalidDataPolicyOverlappingAttributes
import com.getstrm.pace.domain.InvalidDataPolicyOverlappingPrincipals
import com.getstrm.pace.domain.InvalidDataPolicyUnknownGroup
import com.getstrm.pace.domain.ProcessingPlatformNotFoundException
import com.getstrm.pace.snowflake.SnowflakeClient
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import parseDataPolicy
import yaml2json

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
  row_filters:
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
    fun `validate missing age attribute`() {
        val dataPolicy = """
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
    - path_components: [age_missing] # renamed to trigger validation failure
      type: bigint
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
  row_filters:
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
            shouldThrow<InvalidDataPolicyMissingAttribute> {
                underTest.validate(dataPolicy)
            }
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
  row_filters:
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
        coEvery { platforms.getProcessingPlatform(dataPolicy) } throws ProcessingPlatformNotFoundException(dataPolicy.platform.id)
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-detection", "admin")
        runBlocking {
            shouldThrow<ProcessingPlatformNotFoundException> {
                underTest.validate(dataPolicy)
            }
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
  row_filters:
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
            shouldThrow<InvalidDataPolicyNonEmptyLastFieldTransform> {
                underTest.validate(dataPolicy)
            }
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
  row_filters:
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
            shouldThrow<InvalidDataPolicyUnknownGroup> {
                underTest.validate(dataPolicy)
            }
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
  row_filters:
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
            shouldThrow<InvalidDataPolicyOverlappingPrincipals> {
                underTest.validate(dataPolicy)
            }
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
  row_filters:
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
            shouldThrow<InvalidDataPolicyOverlappingAttributes> {
                underTest.validate(dataPolicy)
            }
        }
    }

}

// convenience for tests
private fun groups(vararg group: String) = group.map { Group(it, it, it) }

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
