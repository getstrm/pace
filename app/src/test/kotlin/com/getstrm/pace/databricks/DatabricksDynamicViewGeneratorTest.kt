package com.getstrm.pace.databricks

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import parseDataPolicy
import com.getstrm.pace.util.toSql
import yaml2json

class DatabricksDynamicViewGeneratorTest {

    private lateinit var underTest: DatabricksDynamicViewGenerator

    @BeforeEach
    fun setUp() {
        underTest = DatabricksDynamicViewGenerator(DataPolicy.getDefaultInstance())
    }

    @Test
    fun `fixed string value transform with multiple principals`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("analytics", "marketing"))
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition!!.toSql() shouldBe "((is_account_group_member('analytics')) or (is_account_group_member('marketing')))"
        field shouldBe DSL.`val`("****")
    }

    @Test
    fun `fixed string value transform with a single principal`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .addPrincipals("analytics")
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition!!.toSql() shouldBe "(is_account_group_member('analytics'))"
        field shouldBe DSL.`val`("****")
    }

    @Test
    fun `fixed integer value transform`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("userId").setType("int").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("123"))
            .addPrincipals("analytics")
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition!!.toSql() shouldBe "(is_account_group_member('analytics'))"
        field shouldBe DSL.`val`(123)
    }

    @Test
    fun `fixed string value transform without principals`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition.shouldBeNull()
        field shouldBe DSL.`val`("****")
    }

    @Test
    fun `field transform with three transforms`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val fixedValue = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("marketing", "analytics"))
            .build()
        val otherFixedValue = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("REDACTED EMAIL"))
            .addAllPrincipals(listOf("fraud-detection"))
            .build()
        val fallbackTransform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("stoelpoot"))
            .build()
        val fieldTransform = DataPolicy.RuleSet.FieldTransform.newBuilder()
            .setAttribute(attribute)
            .addAllTransforms(listOf(fixedValue, otherFixedValue, fallbackTransform))
            .build()

        // When
        val field = underTest.toField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe "case when ((is_account_group_member('marketing')) or (is_account_group_member('analytics'))) then '****' when (is_account_group_member('fraud-detection')) then " +
            "'REDACTED EMAIL' else 'stoelpoot' end \"email\""
    }

    @Test
    fun `field transform with two transforms`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val fixedValue = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .addPrincipals("analytics")
            .build()
        val fallbackTransform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("stoelpoot"))
            .build()
        val fieldTransform = DataPolicy.RuleSet.FieldTransform.newBuilder()
            .setAttribute(attribute)
            .addAllTransforms(listOf(fixedValue, fallbackTransform))
            .build()

        // When
        val field = underTest.toField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe "case when (is_account_group_member('analytics')) then '****' else 'stoelpoot' end \"email\""
    }

    @Test
    fun `field transform with single transform`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").build()
        val fallbackTransform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setRegex(DataPolicy.RuleSet.FieldTransform.Transform.RegexReplace.newBuilder().setRegex("^.*(@.*)$").setReplacement("****$1"))
            .build()
        val fieldTransform = DataPolicy.RuleSet.FieldTransform.newBuilder()
            .setAttribute(attribute)
            .addTransforms(fallbackTransform)
            .build()

        // When
        val field = underTest.toField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe "regexp_replace(email, '^.*(@.*)\$', '****\$1') \"email\""
    }

    @Test
    fun `field with no transform`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").build()

        // When
        val field = underTest.toField(attribute, null)

        // Then
        field shouldBe DSL.field("email")
    }

    @Test
    fun `regex replace transform without principals`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setRegex(DataPolicy.RuleSet.FieldTransform.Transform.RegexReplace.newBuilder().setRegex("^.*(@.*)$").setReplacement("****$1"))
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition.shouldBeNull()
        field.toSql() shouldBe "regexp_replace(email, '^.*(@.*)\$', '****\$1')"
    }

    @Test
    fun `regex extract transform with a principal`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setRegex(DataPolicy.RuleSet.FieldTransform.Transform.RegexReplace.newBuilder().setRegex("^.*(@.*)$"))
            .addPrincipals("data-science")
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition shouldBe DSL.condition("is_account_group_member('data-science')")
        field.toSql() shouldBe "regexp_extract(email, '^.*(@.*)\$')"
    }

    @Test
    fun `nullify transform with a principal`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setRegex(DataPolicy.RuleSet.FieldTransform.Transform.RegexReplace.newBuilder().setRegex("^.*(@.*)$"))
            .addPrincipals("data-science")
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition shouldBe DSL.condition("is_account_group_member('data-science')")
        field.toSql() shouldBe "regexp_extract(email, '^.*(@.*)\$')"
    }

    @Test
    fun `hash transform without a principal`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("userId").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setHash(DataPolicy.RuleSet.FieldTransform.Transform.Hash.newBuilder().setSeed(1234))
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition.shouldBeNull()
        field.toSql() shouldBe "hash(1234, userId)"
    }

    @Test
    fun `attribute without transform`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").build()

        // When
        val (condition, field) = underTest.toCase(null, attribute)

        // Then
        condition.shouldBeNull()
        field shouldBe DSL.field("email")
    }

    @Test
    fun `row filter to condition`() {
        // Given
        val filter = DataPolicy.RuleSet.RowFilter.newBuilder()
            .setAttribute(DataPolicy.Attribute.newBuilder().addPathComponents("age").build())
            .addAllConditions(
                listOf(
                    DataPolicy.RuleSet.RowFilter.Condition.newBuilder()
                        .addAllPrincipals(listOf("fraud-detection"))
                        .setCondition("true")
                        .build(),
                    DataPolicy.RuleSet.RowFilter.Condition.newBuilder()
                        .addAllPrincipals(listOf("analytics", "marketing"))
                        .setCondition("age > 18")
                        .build(),
                    DataPolicy.RuleSet.RowFilter.Condition.newBuilder()
                        .setCondition("false")
                        .build()
                )
            )
            .build()

        // When
        val condition = underTest.toCondition(filter)

        // Then
        condition.toSql() shouldBe "case when (is_account_group_member('fraud-detection')) then true when ((is_account_group_member('analytics')) or (is_account_group_member('marketing'))) " +
            "then age > 18 else false end"
    }

    @Test
    fun `transform test all transforms`() {
        underTest = DatabricksDynamicViewGenerator(dataPolicy) { withRenderFormatted(true) }
        underTest.toDynamicViewSQL()
            .shouldBe(
                """create or replace view my_catalog.my_schema.gddemo_public
as
select
  transactionId,
  case
    when (is_account_group_member('fraud-detection')) then userId
    else hash(1234, userId)
  end userId,
  case
    when (
      (is_account_group_member('analytics'))
      or (is_account_group_member('marketing'))
    ) then regexp_replace(email, '^.*(@.*)${'$'}', '****${'$'}1')
    when (
      (is_account_group_member('fraud-detection'))
      or (is_account_group_member('admin'))
    ) then email
    else '****'
  end email,
  age,
  size,
  case when hairColor = 'blonde' then 'fair' else 'dark' end hairColor,
  transactionAmount,
  null items,
  itemCount,
  date,
  purpose
from mycatalog.my_schema.gddemo
where (
  case
    when (is_account_group_member('fraud-detection')) then true
    else age > 18
  end
  and case
    when (is_account_group_member('marketing')) then userId in ('1', '2', '3', '4')
    else true
  end
  and transactionAmount < 10
);"""
            )
        dataPolicy.source.type.shouldBe(DataPolicy.Source.Type.SQL_DDL)
    }

    @Test
    fun `transform - no row filters`() {
        val policyWithoutRowFilters = dataPolicy.toBuilder().apply { ruleSetsBuilderList.first().clearRowFilters() }.build()
        underTest = DatabricksDynamicViewGenerator(policyWithoutRowFilters) { withRenderFormatted(true) }
        underTest.toDynamicViewSQL()
            .shouldBe(
                """create or replace view my_catalog.my_schema.gddemo_public
as
select
  transactionId,
  case
    when (is_account_group_member('fraud-detection')) then userId
    else hash(1234, userId)
  end userId,
  case
    when (
      (is_account_group_member('analytics'))
      or (is_account_group_member('marketing'))
    ) then regexp_replace(email, '^.*(@.*)${'$'}', '****${'$'}1')
    when (
      (is_account_group_member('fraud-detection'))
      or (is_account_group_member('admin'))
    ) then email
    else '****'
  end email,
  age,
  size,
  case when hairColor = 'blonde' then 'fair' else 'dark' end hairColor,
  transactionAmount,
  null items,
  itemCount,
  date,
  purpose
from mycatalog.my_schema.gddemo;"""
            )
        dataPolicy.source.type.shouldBe(DataPolicy.Source.Type.SQL_DDL)
    }

    companion object {
        private val dataPolicy
            get() = """
    source: 
      type: SQL_DDL
      spec: |-
        CREATE TABLE mycatalog.my_schema.gddemo (
          transactionId bigint,
          userId string,
          email string,
          age bigint,
          size string,
          hairColor string,
          transactionAmount bigint,
          items string,
          itemCount bigint,
          date timestamp,
          purpose bigint
        );
      ref: mycatalog.my_schema.gddemo
      attributes:
        - path_components: [transactionId]
          type: bigint
        - path_components: [userId]
          type: string
        - path_components: [email]
          type: string
        - path_components: [age]
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
                value: "****"
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
              nullify: {}
        - attribute:
            path_components:
              - hairColor
          transforms:
            - principals: []
              sql_statement:
                statement: "case when hairColor = 'blonde' then 'fair' else 'dark' end"
      row_filters:
        - attribute:
            path_components:
              - age
          conditions:
            - principals:
                - fraud-detection
              condition: "true"
            - principals: []
              condition: "age > 18"
        - attribute:
            path_components:
              - userId
          conditions:
            - principals:
                - marketing
              condition: "userId in ('1', '2', '3', '4')"
            - principals: []
              condition: "true"
        - attribute:
            path_components:
              - transactionAmount
          conditions:
            - principals: []
              condition: "transactionAmount < 10"
    info:
      title: "Data Policy for GDDemo"
      description: "The demo data policy for the poc with databricks using gddemo dataset."
      version: "1.0.0"
      create_time: "2023-09-26T16:33:51.150Z"
      update_time: "2023-09-26T16:33:51.150Z"
              """.yaml2json().parseDataPolicy()
    }
}
