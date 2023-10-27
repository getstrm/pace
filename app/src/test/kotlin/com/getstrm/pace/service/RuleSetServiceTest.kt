package com.getstrm.pace.service

import com.getstrm.pace.dao.DataPolicyDao
import com.getstrm.pace.dao.RuleSetsDao
import com.getstrm.pace.snowflake.SnowflakeClient
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.getstrm.pace.util.parseDataPolicy
import com.getstrm.pace.util.parseTransforms
import com.getstrm.pace.util.yaml2json

class RuleSetServiceTest {
    private lateinit var underTest: RuleSetService
    private lateinit var dps: DataPolicyService
    private val dao = mockk<DataPolicyDao>()
    private val platforms = mockk<ProcessingPlatformsService>()
    private val jooq = mockk<DSLContext>()
    private val platform = mockk<SnowflakeClient>()
    private val rulesetsDao = mockk<RuleSetsDao>()
    private val defaultContext = "default"

    @BeforeEach
    fun setUp() {
        dps = DataPolicyService(defaultContext, dao, platforms, jooq)
        underTest = RuleSetService(dps, rulesetsDao)
    }

    @Test
    fun addRuleSet() {
        setupGlobalBusinessRules()

        @Language("Yaml")
        val dataPolicy = """
            platform: 
              platform_type: SNOWFLAKE
              id: snowflake
            source:
              ref: test1
              type: SNOWFLAKE
              attributes:
                - path_components: [name]
                  type: varchar
                  tags: [ pii, banaan ]
                - path_components: [email]
                  type: varchar
                  tags: [ email ]
                - path_components: [description]
                  type: varchar
          """.yaml2json().parseDataPolicy()

        runBlocking {
            val policyWithRulesets = underTest.addRuleSet(dataPolicy)!!
            policyWithRulesets shouldBe """
                source:
                  ref: test1
                  type: SNOWFLAKE
                  attributes:
                  - pathComponents:  [ name ]
                    type: varchar
                    tags:  [ pii, banaan ]
                  - pathComponents:  [ email ]
                    type: varchar
                    tags:  [ email ]
                  - path_components: [description]
                    type: varchar
                platform:
                  platformType: SNOWFLAKE
                  id: snowflake
                ruleSets:
                - fieldTransforms:
                  - attribute:
                      pathComponents:  [ name ]
                      type: varchar
                      tags:  [ pii, banaan ]
                    transforms:
                    - principals: [ fraud-detection ]
                      identity: true
                    - hash: {seed: "1234"}
                  - attribute:
                      pathComponents:  [ email ]
                      type: "varchar"
                      tags:  [ "email" ]
                    transforms:
                    - principals:  [ marketing ]
                      regex: {regex: "^.*(@.*)$", replacement: "\\\\1"}
                    - principals: [ fraud-detection ]
                      identity: true
                    - fixed: {value: "****"}
            """.yaml2json().parseDataPolicy()
        }
    }

    @Test
    fun `test overlapping rules`() {
        setupGlobalBusinessRules()

        @Language("Yaml")
        val dataPolicy = """
            platform: 
              platform_type: SNOWFLAKE
              id: snowflake
            source:
              ref: test1
              type: SNOWFLAKE
              attributes:
                - path_components: [name]
                  type: varchar
                  tags: [ pii, banaan, overlap ]
                - path_components: [email]
                  type: varchar
                  tags: [ email, overlap ]
                - path_components: [description]
                  type: varchar
          """.yaml2json().parseDataPolicy()

        runBlocking {
            val policyWithRulesets = underTest.addRuleSet(dataPolicy)!!
            policyWithRulesets shouldBe """
source:
  ref: test1
  type: SNOWFLAKE
  attributes:
  - pathComponents:  [ name ]
    type: varchar
    tags: [ pii,  banaan, overlap ]
  - pathComponents:  [ email ]
    type: varchar
    tags: [  email, overlap ]
  - pathComponents: [ description ]
    type: varchar
platform:
  platformType: SNOWFLAKE
  id: snowflake
ruleSets:
- fieldTransforms:
  - attribute:
      pathComponents: [ name ]
      type: "varchar"
      tags: [  pii, banaan, overlap ]
    transforms:
    - principals:  [ fraud-detection ]
      identity: true
    - principals: [marketing]
      fixed: {value: bla}
    - principals: [ "analytics" ]
      hash: {seed: "3"}
    - hash: {seed: "1234"}
  - attribute:
      pathComponents: [ email ]
      type: varchar
      tags: [  email,  overlap ]
    transforms:
    - principals: [ marketing ]
      regex: {regex: "^.*(@.*)$", replacement: "\\\\1"}
    - principals: [ fraud-detection ]
      identity: true
    - principals: [ analytics ]
      hash: {seed: "3" }
    - fixed: {value: "****" }
         """.yaml2json().parseDataPolicy()
        }
    }

    @Test
    fun `test overlapping reversed tags`() {
        setupGlobalBusinessRules()

        @Language("Yaml")
        val dataPolicy = """
            platform: 
              platform_type: SNOWFLAKE
              id: snowflake
            source:
              ref: test1
              type: SNOWFLAKE
              attributes:
                - path_components: [name]
                  type: varchar
                  tags: [ overlap, pii, banaan ]
                - path_components: [email]
                  type: varchar
                  tags: [ overlap, email ]
                - path_components: [description]
                  type: varchar
          """.yaml2json().parseDataPolicy()

        runBlocking {
            val policyWithRulesets = underTest.addRuleSet(dataPolicy)
            policyWithRulesets shouldBe """
source:
  ref: test1
  type: SNOWFLAKE
  attributes:
  - pathComponents:  [ name ]
    type: varchar
    tags: [  overlap, pii,  banaan ]
  - pathComponents:  [ email ]
    type: varchar
    tags: [  overlap, email ]
  - pathComponents: [ description ]
    type: varchar
platform:
  platformType: SNOWFLAKE
  id: snowflake
ruleSets:
- fieldTransforms:
  - attribute:
      pathComponents: [ name ]
      type: varchar
      # checks ignoring unknown tags
      tags: [  overlap, pii, banaan  ]
    transforms:
    - principals: [marketing]
      fixed: {value: bla}
    - principals: [ analytics ]
      hash: {seed: "3"}
    - principals: [ fraud-detection ]
      nullify: {}
    - fixed: {value: jopie}
  - attribute:
      pathComponents: [ email ]
      type: "varchar"
      tags: [  overlap, email  ]
    transforms:
    - principals: [ marketing ]
      fixed: {value: bla}
    - principals: [ analytics ]
      hash: {seed: "3" }
    - principals: [ fraud-detection ]
      nullify: {}
    - fixed: {value: "jopie" }
            """.yaml2json().parseDataPolicy()
        }
    }

    private fun setupGlobalBusinessRules() {
        coEvery { platforms.getProcessingPlatform(any()) } returns platform
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-detection", "admin")

        // Global business rules
        // a map of field level tags to a list of Api Transforms that define how the
        // field value gets transformed for which group member
        val businessRules = mapOf(
            "email" to """
               transforms:
                  # marketeers get only the domain
                  - principals: [ marketing ]
                    regex: {regex: "^.*(@.*)$", replacement: "\\\\1"}
                  # security gets everything
                  - principals: [ fraud-detection ]
                    identity: true
                  # everyone else gets 4 stars
                  - fixed: {value: "****"}
                """,
            "pii" to """
                # transforms to be applied to fields classified as PII
                transforms:
                # fraud-detection sees the original value
                - principals: [ fraud-detection ]
                  identity: true
                # everyone else gets a hashed value
                - hash: {seed: "1234"}
            """,
            "overlap" to """
               # a business policy that is deliberately overlapping with both others
               transforms:
                  # marketeers get only the domain
                  - principals: [ marketing ]
                    fixed: {value: bla}
                  # analytics gets a hash
                  - principals: [ analytics]
                    hash: {seed: "3" }
                  # security gets null
                  - principals: [ fraud-detection ]
                    nullify: {}
                  # everyone else gets jopie
                  - fixed: {value: jopie}
                """,
        ).mapValues { it.value.parseTransforms() }

        val tagSlot = slot<String>()
        coEvery { rulesetsDao.getFieldTransforms(capture(tagSlot)) } answers {
            businessRules[tagSlot.captured] ?: emptyList()
        }
    }
}
