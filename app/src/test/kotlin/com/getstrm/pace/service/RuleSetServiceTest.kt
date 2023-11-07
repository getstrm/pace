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
    private val platform = mockk<SnowflakeClient>()
    private val rulesetsDao = mockk<RuleSetsDao>()

    @BeforeEach
    fun setUp() {
        dps = DataPolicyService(dao, platforms)
        underTest = RuleSetService(dps, rulesetsDao)
    }

    @Test
    fun addRuleSet() {
        setupGlobalBusinessRules()

        @Language("yaml")
        val dataPolicy = """
            platform: 
              platform_type: SNOWFLAKE
              id: snowflake
            source:
              ref: test1
              type: SNOWFLAKE
              fields:
                - name_parts: [name]
                  type: varchar
                  tags: [ pii, tag123 ]
                - name_parts: [email]
                  type: varchar
                  tags: [ email ]
                - name_parts: [description]
                  type: varchar
          """.yaml2json().parseDataPolicy()

        runBlocking {
            val policyWithRulesets = underTest.addRuleSet(dataPolicy)
            @Language("yaml")
            val result = """
                source:
                  ref: test1
                  type: SNOWFLAKE
                  fields:
                  - nameParts:  [ name ]
                    type: varchar
                    tags:  [ pii, tag123 ]
                  - nameParts:  [ email ]
                    type: varchar
                    tags:  [ email ]
                  - name_parts: [description]
                    type: varchar
                platform:
                  platformType: SNOWFLAKE
                  id: snowflake
                ruleSets:
                - fieldTransforms:
                  - field:
                      nameParts:  [ name ]
                      type: varchar
                      tags:  [ pii, tag123 ]
                    transforms:
                    - principals: [ {group: fraud-and-risk} ]
                      identity: {}
                    - hash: {seed: "1234"}
                  - field:
                      nameParts:  [ email ]
                      type: "varchar"
                      tags:  [ "email" ]
                    transforms:
                    - principals:  [ group: marketing ]
                      regexp: {regexp: "^.*(@.*)$", replacement: "\\\\1"}
                    - principals: [ {group: fraud-and-risk} ]
                      identity: {}
                    - fixed: {value: "****"}
            """
            policyWithRulesets shouldBe result.yaml2json().parseDataPolicy()
        }
    }

    @Test
    fun `test overlapping rules`() {
        setupGlobalBusinessRules()

        @Language("yaml")
        val dataPolicy = """
            platform: 
              platform_type: SNOWFLAKE
              id: snowflake
            source:
              ref: test1
              type: SNOWFLAKE
              fields:
                - name_parts: [name]
                  type: varchar
                  tags: [ pii, tag123, overlap ]
                - name_parts: [email]
                  type: varchar
                  tags: [ email, overlap ]
                - name_parts: [description]
                  type: varchar
          """.yaml2json().parseDataPolicy()

        runBlocking {
            val policyWithRulesets = underTest.addRuleSet(dataPolicy)
            policyWithRulesets shouldBe """
source:
  ref: test1
  type: SNOWFLAKE
  fields:
  - nameParts:  [ name ]
    type: varchar
    tags: [ pii,  tag123, overlap ]
  - nameParts:  [ email ]
    type: varchar
    tags: [  email, overlap ]
  - nameParts: [ description ]
    type: varchar
platform:
  platformType: SNOWFLAKE
  id: snowflake
ruleSets:
- fieldTransforms:
  - field:
      nameParts: [ name ]
      type: "varchar"
      tags: [  pii, tag123, overlap ]
    transforms:
    - principals:  [ {group: fraud-and-risk} ]
      identity: {}
    - principals: [ {group: marketing} ]
      fixed: {value: fixed-value2}
    - principals: [ group: "analytics" ]
      hash: {seed: "3"}
    - hash: {seed: "1234"}
  - field:
      nameParts: [ email ]
      type: varchar
      tags: [  email,  overlap ]
    transforms:
    - principals: [ {group: marketing} ]
      regexp: {regexp: "^.*(@.*)$", replacement: "\\\\1"}
    - principals: [ {group: fraud-and-risk} ]
      identity: {}
    - principals: [ {group: analytics} ]
      hash: {seed: "3" }
    - fixed: {value: "****" }
         """.yaml2json().parseDataPolicy()
        }
    }

    @Test
    fun `test overlapping reversed tags`() {
        setupGlobalBusinessRules()

        @Language("yaml")
        val dataPolicy = """
            platform: 
              platform_type: SNOWFLAKE
              id: snowflake
            source:
              ref: test1
              type: SNOWFLAKE
              fields:
                - name_parts: [name]
                  type: varchar
                  tags: [ overlap, pii, tag123 ]
                - name_parts: [email]
                  type: varchar
                  tags: [ overlap, email ]
                - name_parts: [description]
                  type: varchar
          """.yaml2json().parseDataPolicy()

        runBlocking {
            val policyWithRulesets = underTest.addRuleSet(dataPolicy)
            @Language("yaml")
            val result = """
source:
  ref: test1
  type: SNOWFLAKE
  fields:
  - nameParts:  [ name ]
    type: varchar
    tags: [  overlap, pii,  tag123 ]
  - nameParts:  [ email ]
    type: varchar
    tags: [  overlap, email ]
  - nameParts: [ description ]
    type: varchar
platform:
  platformType: SNOWFLAKE
  id: snowflake
ruleSets:
- fieldTransforms:
  - field:
      nameParts: [ name ]
      type: varchar
      # checks ignoring unknown tags
      tags: [  overlap, pii, tag123  ]
    transforms:
    - principals: [ {group: marketing} ]
      fixed: {value: fixed-value2}
    - principals: [ {group: analytics} ]
      hash: {seed: "3"}
    - principals: [ {group: fraud-and-risk} ]
      nullify: {}
    - fixed: {value: fixed-value}
  - field:
      nameParts: [ email ]
      type: "varchar"
      tags: [  overlap, email  ]
    transforms:
    - principals: [ {group: marketing} ]
      fixed: {value: fixed-value2}
    - principals: [ {group: analytics} ]
      hash: {seed: "3" }
    - principals: [ {group: fraud-and-risk} ]
      nullify: {}
    - fixed: {value: "fixed-value" }
            """
            policyWithRulesets shouldBe result.yaml2json().parseDataPolicy()
        }
    }

    private fun setupGlobalBusinessRules() {
        coEvery { platforms.getProcessingPlatform(any()) } returns platform
        coEvery { platform.listGroups() } returns groups("analytics", "marketing", "fraud-and-risk", "admin")

        // Global business rules
        // a map of field level tags to a list of Api Transforms that define how the
        // field value gets transformed for which group member
        val businessRules = mapOf(
            "email" to """
               transforms:
                  # marketeers get only the domain
                  - principals: [ {group: marketing} ]
                    regexp: {regexp: "^.*(@.*)$", replacement: "\\\\1"}
                  # security gets everything
                  - principals: [ {group: fraud-and-risk} ]
                    identity: {}
                  # everyone else gets 4 stars
                  - fixed: {value: "****"}
                """,
            "pii" to """
                # transforms to be applied to fields classified as PII
                transforms:
                # fraud-and-risk sees the original value
                - principals: [ {group: fraud-and-risk} ]
                  identity: {}
                # everyone else gets a hashed value
                - hash: {seed: "1234"}
            """,
            "overlap" to """
               # a business policy that is deliberately overlapping with both others
               transforms:
                  - principals: [ {group: marketing} ]
                    fixed: {value: fixed-value2}
                  # analytics gets a hash
                  - principals: [ {group: analytics}]
                    hash: {seed: "3" }
                  # security gets null
                  - principals: [ {group: fraud-and-risk} ]
                    nullify: {}
                  # everyone else gets 'fixed-value'
                  - fixed: {value: fixed-value}
                """,
        ).mapValues { it.value.parseTransforms() }

        val tagSlot = slot<String>()
        coEvery { rulesetsDao.getFieldTransforms(capture(tagSlot)) } answers {
            businessRules[tagSlot.captured] ?: emptyList()
        }
    }
}
