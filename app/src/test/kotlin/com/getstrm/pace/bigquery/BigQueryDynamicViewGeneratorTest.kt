package com.getstrm.pace.bigquery

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import parseDataPolicy
import com.getstrm.pace.util.toSql
import yaml2json

class BigQueryDynamicViewGeneratorTest {

    private lateinit var underTest: BigQueryDynamicViewGenerator
    private val defaultUserGroupsTable = "`my_project.my_dataset.my_user_groups`"

    @BeforeEach
    fun setUp() {
        underTest = BigQueryDynamicViewGenerator(DataPolicy.getDefaultInstance(), defaultUserGroupsTable)
    }

    @Test
    fun `fixed value transform with multiple principals`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("ANALYTICS", "MARKETING"))
            .build()

        // when
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition!!.toSql() shouldBe "(('ANALYTICS' IN ( SELECT userGroup FROM user_groups )) or ('MARKETING' IN ( SELECT userGroup FROM user_groups )))"
        field shouldBe DSL.`val`("****")
    }

    @Test
    fun `fixed value transform with with a single principal`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .addPrincipals("MARKETING")
            .build()

        // when
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition!!.toSql() shouldBe "('MARKETING' IN ( SELECT userGroup FROM user_groups ))"
        field shouldBe DSL.`val`("****")
    }

    @Test
    fun `fixed value transform with without principals`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .build()

        // when
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition.shouldBeNull()
        field shouldBe DSL.`val`("****")
    }

    @Test
    fun `field transform with a few transforms`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val fixedValue = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("ANALYTICS", "MARKETING"))
            .build()
        val otherFixedValue = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("REDACTED EMAIL"))
            .addAllPrincipals(listOf("FRAUD_DETECTION"))
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
        field.toSql() shouldBe "case when (('ANALYTICS' IN ( SELECT userGroup FROM user_groups )) or ('MARKETING' IN ( SELECT userGroup FROM user_groups ))) then '****' when ('FRAUD_DETECTION' " +
            "IN ( SELECT userGroup FROM user_groups )) then 'REDACTED EMAIL' else 'stoelpoot' end \"email\""
    }

    @Test
    fun `row filters to condition`() {
        // Given
        val filter = DataPolicy.RuleSet.Filter.newBuilder()
            .addAllConditions(
                listOf(
                    DataPolicy.RuleSet.Filter.Condition.newBuilder()
                        .addAllPrincipals(listOf("fraud-detection"))
                        .setCondition("true")
                        .build(),
                    DataPolicy.RuleSet.Filter.Condition.newBuilder()
                        .addAllPrincipals(listOf("analytics", "marketing"))
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
        condition.toSql() shouldBe "case when ('fraud-detection' IN ( SELECT userGroup FROM user_groups )) then true when (('analytics' IN ( SELECT userGroup FROM user_groups )) or ('marketing' IN ( SELECT userGroup FROM user_groups ))) then age > 18 else false end"
    }

    @Test
    fun `transform test all transforms`() {
        // Given
        underTest = BigQueryDynamicViewGenerator(dataPolicy, defaultUserGroupsTable) { withRenderFormatted(true) }
        underTest.toDynamicViewSQL()
            .shouldBe(
                """create or replace view `my_target_project.my_target_dataset.my_target_view`
as
with
  user_groups as (
    select userGroup
    from `my_project.my_dataset.my_user_groups`
    where userEmail = SESSION_USER()
  )
select
  transactionId,
  case
    when ('FRAUD_DETECTION' IN ( SELECT userGroup FROM user_groups )) then CAST(userId AS string)
    else TO_HEX(SHA256(CAST(userId AS string)))
  end userId,
  case
    when (
      ('ANALYTICS' IN ( SELECT userGroup FROM user_groups ))
      or ('MARKETING' IN ( SELECT userGroup FROM user_groups ))
    ) then regexp_replace(email, '^.*(@.*)${'$'}', '****\\1')
    when (
      ('FRAUD_DETECTION' IN ( SELECT userGroup FROM user_groups ))
      or ('ADMIN' IN ( SELECT userGroup FROM user_groups ))
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
from `my_project.my_dataset.gddemo`
where (
  case
    when ('FRAUD_DETECTION' IN ( SELECT userGroup FROM user_groups )) then true
    else age > 18
  end
  and case
    when ('MARKETING' IN ( SELECT userGroup FROM user_groups )) then userId in ('1', '2', '3', '4')
    else true
  end
  and transactionAmount < 10
);"""
            )
        dataPolicy.source.type.shouldBe(DataPolicy.Source.Type.SQL_DDL)
    }

    companion object {
        val dataPolicy = """
source:
  type: SQL_DDL
  spec: |-
    CREATE TABLE my_project.my_dataset.gddemo (
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
  ref: "my_project.my_dataset.gddemo"
  attributes:
    - path_components: [transactionId]
      type: bigint
    - path_components: [userId]
      type: string
    - path_components: [email]
      type: string
    - path_components: [age]
      type: NUMBER(38,0)
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
    fullname: 'my_target_project.my_target_dataset.my_target_view'
  field_transforms:
    - attribute:
        path_components:
          - email
        type: "string"
      transforms:
        - principals:
            - ANALYTICS
            - MARKETING
          regex:
            regex: '^.*(@.*)${'$'}'
            replacement: '****\\1'
        - principals:
            - FRAUD_DETECTION
            - ADMIN
          identity: true
        - principals: []
          fixed:
            value: "****"
    - attribute:
        path_components:
          - userId
      transforms:
        - principals:
            - FRAUD_DETECTION
          sql_statement:
            statement: "CAST(userId AS string)"
        - principals: []
          sql_statement:
            statement: "TO_HEX(SHA256(CAST(userId AS string)))"
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
  filters:
    - attribute:
        path_components:
          - age
      conditions:
        - principals:
            - FRAUD_DETECTION
          condition: "true"
        - principals: []
          condition: "age > 18"
    - attribute:
        path_components:
          - userId
      conditions:
        - principals:
            - MARKETING
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
