package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.RefAndType
import com.getstrm.jooq.generated.tables.records.GlobalTransformsRecord
import com.getstrm.pace.dao.DataPolicyDao
import com.getstrm.pace.dao.GlobalTransformsDao
import com.getstrm.pace.processing_platforms.snowflake.SnowflakeClient
import com.getstrm.pace.util.parseDataPolicy
import com.getstrm.pace.util.parseTransforms
import com.getstrm.pace.util.toJsonbWithDefaults
import com.getstrm.pace.util.yaml2json
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlobalTransformsServiceTest {
    private lateinit var underTest: GlobalTransformsService
    private lateinit var dps: DataPolicyService
    private val dao = mockk<DataPolicyDao>()
    private val platforms = mockk<ProcessingPlatformsService>()
    private val platform = mockk<SnowflakeClient>()
    private val rulesetsDao = mockk<GlobalTransformsDao>()

    @BeforeEach
    fun setUp() {
        dps = DataPolicyService(dao, platforms)
        underTest = GlobalTransformsService(dps, rulesetsDao)
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
        val businessRules = """
global_transforms:
  - ref: irrelevant
    description: email
    tag_transform:
      tag_content: email
      transforms:
        # marketeers get only the domain
        - principals: [ {group: marketing} ]
          regexp: {regexp: "^.*(@.*)$", replacement: "\\\\1"}
        # security gets everything
        - principals: [ {group: fraud-and-risk} ]
          identity: {}
        # everyone else gets 4 stars
        - fixed: {value: "****"}
  - ref: also irrelevant
    description: pii
    tag_transform:
      tag_content: pii
      # transforms to be applied to fields classified as PII
      transforms:
        # fraud-and-risk sees the original value
        - principals: [ {group: fraud-and-risk} ]
          identity: {}
        # everyone else gets a hashed value
        - hash: {seed: "1234"}
  - ref: overlap
    description: overlap
    tag_transform:
      tag_content: overlap
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
    """.parseTransforms().associate {
            RefAndType.newBuilder()
                .setRef(it.tagTransform.tagContent)
                .setType(GlobalTransform.TransformCase.TAG_TRANSFORM.name)
                .build() to it.toRecord()
        }

        val tagSlot = slot<RefAndType>()
        coEvery {
            rulesetsDao.getTransform(capture(tagSlot))
        } answers { // ktlint-disable max-line-length
            businessRules[tagSlot.captured] ?: GlobalTransform.getDefaultInstance().toRecord()
        }
    }

    companion object {
        private fun GlobalTransform.toRecord(): GlobalTransformsRecord {
            val record = GlobalTransformsRecord()
            record.ref = this.ref
            record.transformType = this.transformCase.name
            record.transform = this.toJsonbWithDefaults()
            return record
        }
    }
}