package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.TransformCase.TAG_TRANSFORM
import build.buf.gen.getstrm.pace.api.global_transforms.v1alpha.ListGlobalTransformsResponse
import com.getstrm.jooq.generated.tables.records.GlobalTransformsRecord
import com.getstrm.pace.config.AppConfiguration
import com.getstrm.pace.dao.GlobalTransformsDao
import com.getstrm.pace.processing_platforms.addRuleSet
import com.getstrm.pace.util.*
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
    private val globalTransformsDao = mockk<GlobalTransformsDao>()

    @BeforeEach
    fun setUp() {
        underTest =
            GlobalTransformsService(
                AppConfiguration(defaultViewSuffix = "_view"),
                globalTransformsDao
            )
    }

    @Test
    fun addRuleSet() {
        setupGlobalTagTransforms()

        @Language("yaml")
        val dataPolicy =
            """
source:
  ref:
    integration_fqn: public.test1
    platform:
      platform_type: SNOWFLAKE
      id: snowflake
  fields:
    - name_parts: [name]
      type: varchar
      tags: [ pii, tag123 ]
    - name_parts: [email]
      type: varchar
      tags: [ email ]
    - name_parts: [description]
      type: varchar

              """
                .toProto<DataPolicy>()

        runBlocking {
            val policyWithRulesets =
                addRuleSet(dataPolicy) { tag: String ->
                    underTest.getTransformOrNull(tag, GlobalTransform.TransformCase.TAG_TRANSFORM)
                }

            @Language("yaml")
            val result =
                """
source:
  ref:
    integration_fqn: public.test1
    platform:
      platform_type: SNOWFLAKE
      id: snowflake
  fields:
    - name_parts:  [ name ]
      type: varchar
      tags:  [ pii, tag123 ]
    - name_parts:  [ email ]
      type: varchar
      tags:  [ email ]
    - name_parts: [description]
      type: varchar
rule_sets:
  - target:
      ref: 
        integration_fqn: "public.test1_view"
    field_transforms:  
      - field:
          name_parts:  [ name ]
          type: varchar
          tags:  [ pii, tag123 ]
        transforms:
          - principals: [ {group: fraud-and-risk} ]
            identity: {}
          - hash: {seed: "1234"}
      - field:
          name_parts:  [ email ]
          type: "varchar"
          tags:  [ "email" ]
        transforms:
          - principals:  [ group: marketing ]
            regexp: {regexp: "^.*(@.*)$", replacement: "$1"}
          - principals: [ {group: fraud-and-risk} ]
            identity: {}
          - fixed: {value: "****"}
            """
                    .toProto<DataPolicy>()
            policyWithRulesets shouldBe result
        }
    }

    @Test
    fun `test overlapping rules`() {
        setupGlobalTagTransforms()

        @Language("yaml")
        val dataPolicy =
            """
source:
  ref:
    integration_fqn: test1
    platform:
      platform_type: SNOWFLAKE
      id: snowflake
  fields:
    - name_parts: [name]
      type: varchar
      tags: [ pii, tag123, overlap ]
    - name_parts: [email]
      type: varchar
      tags: [ email, overlap ]
    - name_parts: [description]
      type: varchar
              """
                .toProto<DataPolicy>()

        runBlocking {
            val policyWithRulesets =
                addRuleSet(dataPolicy) { tag: String ->
                    underTest.getTransformOrNull(tag, GlobalTransform.TransformCase.TAG_TRANSFORM)
                }

            @Language("yaml")
            val expected =
                """
source:
  ref:
    integration_fqn: test1
    platform:
      platform_type: SNOWFLAKE
      id: snowflake
  fields:
    - name_parts:  [ name ]
      type: varchar
      tags: [ pii,  tag123, overlap ]
    - name_parts:  [ email ]
      type: varchar
      tags: [  email, overlap ]
    - name_parts: [ description ]
      type: varchar
rule_sets:
  - target:
      ref: 
        integration_fqn: "test1_view"
    field_transforms:  
      - field:
          name_parts: [ name ]
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
          name_parts: [ email ]
          type: varchar
          tags: [  email,  overlap ]
        transforms:
          - principals: [ {group: marketing} ]
            regexp: {regexp: "^.*(@.*)$", replacement: "$1"}
          - principals: [ {group: fraud-and-risk} ]
            identity: {}
          - principals: [ {group: analytics} ]
            hash: {seed: "3" }
          - fixed: {value: "****" }
                 """
                    .trimIndent()
                    .toProto<DataPolicy>()

            policyWithRulesets shouldBe expected
        }
    }

    @Test
    fun `test overlapping reversed tags`() {
        setupGlobalTagTransforms()

        @Language("yaml")
        val dataPolicy =
            """
source:
  ref:
    integration_fqn: test1
    platform:
      platform_type: SNOWFLAKE
      id: snowflake
  fields:
    - name_parts: [name]
      type: varchar
      tags: [ overlap, pii, tag123 ]
    - name_parts: [email]
      type: varchar
      tags: [ overlap, email ]
    - name_parts: [description]
      type: varchar
              """
                .toProto<DataPolicy>()

        runBlocking {
            val policyWithRulesets =
                addRuleSet(dataPolicy) { tag: String ->
                    underTest.getTransformOrNull(tag, GlobalTransform.TransformCase.TAG_TRANSFORM)
                }

            @Language("yaml")
            val expected =
                """
source:
  ref:
    integration_fqn: test1
    platform:
      platform_type: SNOWFLAKE
      id: snowflake
  fields:
    - name_parts:  [ name ]
      type: varchar
      tags: [  overlap, pii,  tag123 ]
    - name_parts:  [ email ]
      type: varchar
      tags: [  overlap, email ]
    - name_parts: [ description ]
      type: varchar
rule_sets:
  - target:
      ref: 
        integration_fqn: "test1_view"
    field_transforms:  
      - field:
          name_parts: [ name ]
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
          name_parts: [ email ]
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
                    .trimIndent()
                    .toProto<DataPolicy>()

            policyWithRulesets shouldBe expected
        }
    }

    private fun setupGlobalTagTransforms() {
        // Global business rules
        // a map of field level tags to a list of Api Transforms that define how the
        // field value gets transformed for which group member
        @Language("yaml")
        val businessRules =
            """
                    global_transforms:
                      - description: email
                        tag_transform:
                          tag_content: email
                          transforms:
                            # marketeers get only the domain
                            - principals: [ {group: marketing} ]
                              regexp: {regexp: "^.*(@.*)$", replacement: "$1"}
                            # security gets everything
                            - principals: [ {group: fraud-and-risk} ]
                              identity: {}
                            # everyone else gets 4 stars
                            - fixed: {value: "****"}
                      - description: pii
                        tag_transform:
                          tag_content: pii
                          # transforms to be applied to fields classified as PII
                          transforms:
                            # fraud-and-risk sees the original value
                            - principals: [ {group: fraud-and-risk} ]
                              identity: {}
                            # everyone else gets a hashed value
                            - hash: {seed: "1234"}
                      - description: overlap
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
            """
                .trimIndent()
                .toProto<ListGlobalTransformsResponse>()
                .globalTransformsList
                .associate { it.tagTransform.tagContent to it.toRecord() }

        val refSlot = slot<String>()
        coEvery { globalTransformsDao.getTransform(capture(refSlot), TAG_TRANSFORM) } answers
            { // ktlint-disable max-line-length
                businessRules[refSlot.captured]
                    ?: GlobalTransform.newBuilder()
                        .setTagTransform(
                            GlobalTransform.TagTransform.newBuilder()
                                .setTagContent(refSlot.captured)
                        )
                        .build()
                        .toRecord()
            }
    }

    companion object {
        private fun GlobalTransform.toRecord(): GlobalTransformsRecord {
            val record = GlobalTransformsRecord()
            record.ref = this.refAndType().first
            record.transformType = this.transformCase.name
            record.transform = this.toJsonbWithDefaults()
            return record
        }
    }
}
