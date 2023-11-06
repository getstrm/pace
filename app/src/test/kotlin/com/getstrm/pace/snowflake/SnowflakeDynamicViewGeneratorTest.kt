package com.getstrm.pace.snowflake

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.processing_platforms.snowflake.SnowflakeDynamicViewGenerator
import com.getstrm.pace.toPrincipal
import com.getstrm.pace.toPrincipals
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.getstrm.pace.util.parseDataPolicy
import com.getstrm.pace.toSql
import com.getstrm.pace.util.yaml2json
import org.intellij.lang.annotations.Language

class SnowflakeDynamicViewGeneratorTest {

    private lateinit var underTest: SnowflakeDynamicViewGenerator

    @BeforeEach
    fun setUp() {
        underTest = SnowflakeDynamicViewGenerator(DataPolicy.getDefaultInstance())
    }

    @Test
    fun `fixed value transform with multiple principals`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("ANALYTICS", "MARKETING").toPrincipals())
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition!!.toSql() shouldBe "((IS_ROLE_IN_SESSION('ANALYTICS')) or (IS_ROLE_IN_SESSION('MARKETING')))"
        field.toSql() shouldBe "'****'"
    }

    @Test
    fun `fixed value transform with a single principal`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addPrincipals("MARKETING".toPrincipal())
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition!!.toSql() shouldBe "(IS_ROLE_IN_SESSION('MARKETING'))"
        field.toSql() shouldBe "'****'"
    }

    @Test
    fun `fixed value transform without principals`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition.shouldBeNull()
        field.toSql() shouldBe "'****'"
    }

    @Test
    fun `field transform with a few transforms`() {
        // Given
        val field = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val fixed =DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("ANALYTICS", "MARKETING").toPrincipals())
            .build()
        val otherFixed =DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("REDACTED EMAIL"))
            .addAllPrincipals(listOf("FRAUD_AND_RISK").toPrincipals())
            .build()
        val fallbackTransform =DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("stoelpoot"))
            .build()
        val fieldTransform =DataPolicy.RuleSet.FieldTransform.newBuilder()
            .setField(field)
            .addAllTransforms(listOf(fixed, otherFixed, fallbackTransform))
            .build()

        // When
        val jooqField = underTest.toField(field, fieldTransform)

        // Then
        jooqField.toSql() shouldBe "case when ((IS_ROLE_IN_SESSION('ANALYTICS')) or (IS_ROLE_IN_SESSION('MARKETING'))) then '****' when (IS_ROLE_IN_SESSION('FRAUD_AND_RISK')) then 'REDACTED EMAIL' " +
                "else 'stoelpoot' end \"email\""
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
        condition.toSql() shouldBe "case when (IS_ROLE_IN_SESSION('fraud-and-risk')) then true when ((IS_ROLE_IN_SESSION('analytics')) " +
                "or (IS_ROLE_IN_SESSION('marketing'))) then age > 18 else false end"
    }

    @Test
    fun `transform test all transforms`() {
        // Given
        underTest = SnowflakeDynamicViewGenerator(dataPolicy) { withRenderFormatted(true) }
        underTest.toDynamicViewSQL()
            .shouldBe(
                """create or replace view my_database.my_schema.gddemo_public
as
select
  transactionId,
  case
    when (IS_ROLE_IN_SESSION('FRAUD_AND_RISK')) then userId
    else hash(1234, userId)
  end userId,
  case
    when (
      (IS_ROLE_IN_SESSION('ANALYTICS'))
      or (IS_ROLE_IN_SESSION('MARKETING'))
    ) then regexp_replace(email, '^.*(@.*)${'$'}', '****\\1')
    when (
      (IS_ROLE_IN_SESSION('FRAUD_AND_RISK'))
      or (IS_ROLE_IN_SESSION('ADMIN'))
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
from mydb.my_schema.gddemo
where (
  case
    when (IS_ROLE_IN_SESSION('FRAUD_AND_RISK')) then true
    else age > 18
  end
  and case
    when (IS_ROLE_IN_SESSION('MARKETING')) then userId in ('1', '2', '3', '4')
    else true
  end
  and transactionAmount < 10
);
grant SELECT on my_database.my_schema.gddemo_public to ANALYTICS;
grant SELECT on my_database.my_schema.gddemo_public to MARKETING;
grant SELECT on my_database.my_schema.gddemo_public to FRAUD_AND_RISK;
grant SELECT on my_database.my_schema.gddemo_public to ADMIN;"""
            )
    }

    companion object {
        @Language("yaml")
        val dataPolicy = """
source: 
  ref: mydb.my_schema.gddemo
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
    fullname: 'my_database.my_schema.gddemo_public'
  field_transforms:
    - field:
        name_parts:
          - email
      transforms:
        - principals:
            - group: ANALYTICS
            - group: MARKETING
          regexp:
            regexp: '^.*(@.*)${'$'}'
            replacement: '****\\1'
        - principals:
            - group: FRAUD_AND_RISK
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
            - group: FRAUD_AND_RISK
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
            - group: FRAUD_AND_RISK
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
