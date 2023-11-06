package com.getstrm.pace.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.processing_platforms.databricks.DatabricksDynamicViewGenerator
import com.getstrm.pace.toPrincipal
import com.getstrm.pace.toPrincipals
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.getstrm.pace.util.parseDataPolicy
import com.getstrm.pace.toSql
import com.getstrm.pace.util.yaml2json
import org.intellij.lang.annotations.Language

class DatabricksDynamicViewGeneratorTest {

    private lateinit var underTest: DatabricksDynamicViewGenerator

    @BeforeEach
    fun setUp() {
        underTest = DatabricksDynamicViewGenerator(DataPolicy.getDefaultInstance())
    }

    @Test
    fun `fixed string value transform with multiple principals`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("analytics", "marketing").toPrincipals())
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
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addPrincipals("analytics".toPrincipal())
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
        val attribute = DataPolicy.Field.newBuilder().addNameParts("userId").setType("int").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("123"))
            .addPrincipals("analytics".toPrincipal())
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
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
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
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val fixed = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("marketing", "analytics").toPrincipals())
            .build()
        val otherFixed = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("REDACTED EMAIL"))
            .addAllPrincipals(listOf("fraud-and-risk").toPrincipals())
            .build()
        val fallbackTransform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("stoelpoot"))
            .build()
        val fieldTransform = DataPolicy.RuleSet.FieldTransform.newBuilder()
            .setField(attribute)
            .addAllTransforms(listOf(fixed, otherFixed, fallbackTransform))
            .build()

        // When
        val field = underTest.toField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe "case when ((is_account_group_member('marketing')) or (is_account_group_member('analytics'))) then '****' when (is_account_group_member('fraud-and-risk')) then " +
            "'REDACTED EMAIL' else 'stoelpoot' end \"email\""
    }

    @Test
    fun `field transform with two transforms`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val fixed = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addPrincipals("analytics".toPrincipal())
            .build()
        val fallbackTransform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("stoelpoot"))
            .build()
        val fieldTransform = DataPolicy.RuleSet.FieldTransform.newBuilder()
            .setField(attribute)
            .addAllTransforms(listOf(fixed, fallbackTransform))
            .build()

        // When
        val field = underTest.toField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe "case when (is_account_group_member('analytics')) then '****' else 'stoelpoot' end \"email\""
    }

    @Test
    fun `field transform with single transform`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").build()
        val fallbackTransform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setRegexp(DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder().setRegexp("^.*(@.*)$").setReplacement("****$1"))
            .build()
        val fieldTransform = DataPolicy.RuleSet.FieldTransform.newBuilder()
            .setField(attribute)
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
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").build()

        // When
        val field = underTest.toField(attribute, null)

        // Then
        field shouldBe DSL.field("email")
    }

    @Test
    fun `regex replace transform without principals`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setRegexp(DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder().setRegexp("^.*(@.*)$").setReplacement("****$1"))
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
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setRegexp(DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder().setRegexp("^.*(@.*)$"))
            .addPrincipals("data-science".toPrincipal())
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
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setRegexp(DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder().setRegexp("^.*(@.*)$"))
            .addPrincipals("data-science".toPrincipal())
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
        val attribute = DataPolicy.Field.newBuilder().addNameParts("userId").build()
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
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").build()

        // When
        val (condition, field) = underTest.toCase(null, attribute)

        // Then
        condition.shouldBeNull()
        field shouldBe DSL.field("email")
    }

    @Test
    fun `row filter to condition`() {
        // Given
        val filter = DataPolicy.RuleSet.Filter.newBuilder()
            .addAllConditions(
                listOf(
                    DataPolicy.RuleSet.Filter.Condition.newBuilder()
                        .addAllPrincipals(listOf("fraud-and-risk").toPrincipals())
                        .setCondition("true")
                        .build(),
                    DataPolicy.RuleSet.Filter.Condition.newBuilder()
                        .addAllPrincipals(listOf("analytics", "marketing").toPrincipals())
                        .setCondition("age > 18")
                        .build(),
                    DataPolicy.RuleSet.Filter.Condition.newBuilder()
                        .setCondition("false")
                        .build()
                )
            )
            .build()

        // When
        val condition = underTest.toCondition(filter)

        // Then
        condition.toSql() shouldBe "case when (is_account_group_member('fraud-and-risk')) then true when ((is_account_group_member('analytics')) or (is_account_group_member('marketing'))) " +
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
    when (is_account_group_member('fraud-and-risk')) then userId
    else hash(1234, userId)
  end userId,
  case
    when (
      (is_account_group_member('analytics'))
      or (is_account_group_member('marketing'))
    ) then regexp_replace(email, '^.*(@.*)${'$'}', '****${'$'}1')
    when (
      (is_account_group_member('fraud-and-risk'))
      or (is_account_group_member('admin'))
    ) then email
    else '****'
  end email,
  age,
  size,
  case when brand = 'Macbook' then 'Apple' else 'Other' end brand,
  transactionAmount,
  null items,
  itemCount,
  date,
  purpose
from mycatalog.my_schema.gddemo
where (
  case
    when (is_account_group_member('fraud-and-risk')) then true
    else age > 18
  end
  and case
    when (is_account_group_member('marketing')) then userId in ('1', '2', '3', '4')
    else true
  end
  and transactionAmount < 10
);"""
            )
    }

    @Test
    fun `transform - no row filters`() {
        val policyWithoutFilters = dataPolicy.toBuilder().apply { ruleSetsBuilderList.first().clearFilters() }.build()
        underTest = DatabricksDynamicViewGenerator(policyWithoutFilters) { withRenderFormatted(true) }
        underTest.toDynamicViewSQL()
            .shouldBe(
                """create or replace view my_catalog.my_schema.gddemo_public
as
select
  transactionId,
  case
    when (is_account_group_member('fraud-and-risk')) then userId
    else hash(1234, userId)
  end userId,
  case
    when (
      (is_account_group_member('analytics'))
      or (is_account_group_member('marketing'))
    ) then regexp_replace(email, '^.*(@.*)${'$'}', '****${'$'}1')
    when (
      (is_account_group_member('fraud-and-risk'))
      or (is_account_group_member('admin'))
    ) then email
    else '****'
  end email,
  age,
  size,
  case when brand = 'Macbook' then 'Apple' else 'Other' end brand,
  transactionAmount,
  null items,
  itemCount,
  date,
  purpose
from mycatalog.my_schema.gddemo;"""
            )
    }

    companion object {
        @Language("yaml")
        private val dataPolicy = """
    source: 
      type: SQL_DDL
      spec: |-
        CREATE TABLE mycatalog.my_schema.gddemo (
          transactionId bigint,
          userId string,
          email string,
          age bigint,
          size string,
          brand string,
          transactionAmount bigint,
          items string,
          itemCount bigint,
          date timestamp,
          purpose bigint
        );
      ref: mycatalog.my_schema.gddemo
      fields:
        - name_parts: [transactionId]
          type: bigint
        - name_parts: [userId]
          type: string
        - name_parts: [email]
          type: string
        - name_parts: [age]
          type: bigint
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
                value: "****"
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
              nullify: {}
        - field:
            name_parts:
              - brand
          transforms:
            - principals: []
              sql_statement:
                statement: "case when brand = 'Macbook' then 'Apple' else 'Other' end"
      filters:
        - field:
            name_parts:
              - age
          conditions:
            - principals:
                - group: fraud-and-risk
              condition: "true"
            - principals: []
              condition: "age > 18"
        - field:
            name_parts:
              - userId
          conditions:
            - principals:
                - group: marketing
              condition: "userId in ('1', '2', '3', '4')"
            - principals: []
              condition: "true"
        - field:
            name_parts:
              - transactionAmount
          conditions:
            - principals: []
              condition: "transactionAmount < 10"
    info:
      title: "Data Policy for Pace Databricks Demo Dataset"
      description: "Pace Demo Dataset"
      version: "1.0.0"
      create_time: "2023-09-26T16:33:51.150Z"
      update_time: "2023-09-26T16:33:51.150Z"
              """.yaml2json().parseDataPolicy()
    }
}
