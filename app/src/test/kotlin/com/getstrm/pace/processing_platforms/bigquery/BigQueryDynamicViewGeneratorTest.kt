package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.Principal
import com.getstrm.pace.processing_platforms.bigquery.BigQueryDynamicViewGenerator
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
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("ANALYTICS", "MARKETING").map { Principal.newBuilder().setGroup(it).build() })
            .build()

        // when
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition!!.toSql() shouldBe "(('ANALYTICS' IN ( SELECT userGroup FROM user_groups )) or ('MARKETING' IN ( SELECT userGroup FROM user_groups )))"
        field shouldBe DSL.`val`("****")
    }

    @Test
    fun `fixed value transform with a single principal`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addPrincipals("MARKETING".toPrincipal())
            .build()

        // when
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition!!.toSql() shouldBe "('MARKETING' IN ( SELECT userGroup FROM user_groups ))"
        field shouldBe DSL.`val`("****")
    }

    @Test
    fun `fixed value transform without principals`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
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
        val field = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val fixed = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("ANALYTICS", "MARKETING").toPrincipals())
            .build()
        val otherFixed = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("REDACTED EMAIL"))
            .addAllPrincipals(listOf("FRAUD_DETECTION").toPrincipals())
            .build()
        val fallbackTransform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("fixed-value"))
            .build()
        val fieldTransform = DataPolicy.RuleSet.FieldTransform.newBuilder()
            .setField(field)
            .addAllTransforms(listOf(fixed, otherFixed, fallbackTransform))
            .build()

        // When
        val jooqField = underTest.toJooqField(field, fieldTransform)

        // Then
        jooqField.toSql() shouldBe "case when (('ANALYTICS' IN ( SELECT userGroup FROM user_groups )) or ('MARKETING' IN ( SELECT userGroup FROM user_groups ))) then '****' when ('FRAUD_DETECTION' " +
            "IN ( SELECT userGroup FROM user_groups )) then 'REDACTED EMAIL' else 'fixed-value' end \"email\""
    }

    @Test
    fun `row filters to condition`() {
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
        condition.toSql() shouldBe "case when ('fraud-and-risk' IN ( SELECT userGroup FROM user_groups )) then true when (('analytics' IN ( SELECT userGroup FROM user_groups )) or ('marketing' IN ( SELECT userGroup FROM user_groups ))) then age > 18 else false end"
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
  case when brand = 'Macbook' then 'Apple' else 'Other' end brand,
  transactionAmount,
  null items,
  itemCount,
  date,
  purpose
from `my_project.my_dataset.my_table`
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
    }

    companion object {
        @Language("yaml")
        val dataPolicy = """
source:
  ref: "my_project.my_dataset.my_table"
  fields:
    - name_parts: [transactionId]
      type: bigint
    - name_parts: [userId]
      type: string
    - name_parts: [email]
      type: string
    - name_parts: [age]
      type: NUMBER(38,0)
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
    fullname: 'my_target_project.my_target_dataset.my_target_view'
  field_transforms:
    - field:
        name_parts:
          - email
        type: "string"
      transforms:
        - principals:
            - group: ANALYTICS
            - group: MARKETING
          regexp:
            regexp: '^.*(@.*)${'$'}'
            replacement: '****\\1'
        - principals:
            - group: FRAUD_DETECTION
            - group: ADMIN
          identity: {}
        - principals: []
          fixed:
            value: "****"
    - field:
        name_parts:
          - userId
      transforms:
        - principals:
            - group: FRAUD_DETECTION
          sql_statement:
            statement: "CAST(userId AS string)"
        - principals: []
          sql_statement:
            statement: "TO_HEX(SHA256(CAST(userId AS string)))"
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
            - group: FRAUD_DETECTION
          condition: "true"
        - principals: []
          condition: "age > 18"
    - field:
        name_parts:
          - userId
      conditions:
        - principals:
            - group: MARKETING
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
  title: "Data Policy for Pace BigQuery Demo Dataset"
  description: "Pace Demo Dataset"
  version: "1.0.0"
  create_time: "2023-09-26T16:33:51.150Z"
  update_time: "2023-09-26T16:33:51.150Z"
          """.yaml2json().parseDataPolicy()
    }
}
