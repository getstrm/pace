package com.getstrm.daps.snowflake

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import parseDataPolicy
import com.getstrm.daps.util.toSql
import yaml2json

class SnowflakeDynamicViewGeneratorTest {

    private lateinit var underTest: SnowflakeDynamicViewGenerator

    @BeforeEach
    fun setUp() {
        underTest = SnowflakeDynamicViewGenerator(DataPolicy.getDefaultInstance())
    }

    @Test
    fun `fixed value transform with multiple principals`() {
        // Given
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("ANALYTICS", "MARKETING"))
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
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .addPrincipals("MARKETING")
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
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
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
        val attribute = DataPolicy.Attribute.newBuilder().addPathComponents("email").setType("string").build()
        val fixedValue =DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("ANALYTICS", "MARKETING"))
            .build()
        val otherFixedValue =DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("REDACTED EMAIL"))
            .addAllPrincipals(listOf("FRAUD_DETECTION"))
            .build()
        val fallbackTransform =DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.FixedValue.newBuilder().setValue("stoelpoot"))
            .build()
        val fieldTransform =DataPolicy.RuleSet.FieldTransform.newBuilder()
            .setAttribute(attribute)
            .addAllTransforms(listOf(fixedValue, otherFixedValue, fallbackTransform))
            .build()

        // When
        val field = underTest.toField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe "case when ((IS_ROLE_IN_SESSION('ANALYTICS')) or (IS_ROLE_IN_SESSION('MARKETING'))) then '****' when (IS_ROLE_IN_SESSION('FRAUD_DETECTION')) then 'REDACTED EMAIL' " +
                "else 'stoelpoot' end \"email\""
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
        condition.toSql() shouldBe "case when (IS_ROLE_IN_SESSION('fraud-detection')) then true when ((IS_ROLE_IN_SESSION('analytics')) " +
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
    when (IS_ROLE_IN_SESSION('FRAUD_DETECTION')) then userId
    else hash(1234, userId)
  end userId,
  case
    when (
      (IS_ROLE_IN_SESSION('ANALYTICS'))
      or (IS_ROLE_IN_SESSION('MARKETING'))
    ) then regexp_replace(email, '^.*(@.*)${'$'}', '****\\1')
    when (
      (IS_ROLE_IN_SESSION('FRAUD_DETECTION'))
      or (IS_ROLE_IN_SESSION('ADMIN'))
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
from mydb.my_schema.gddemo
where (
  case
    when (IS_ROLE_IN_SESSION('FRAUD_DETECTION')) then true
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
grant SELECT on my_database.my_schema.gddemo_public to FRAUD_DETECTION;
grant SELECT on my_database.my_schema.gddemo_public to ADMIN;"""
            )
        dataPolicy.source.type.shouldBe(DataPolicy.Source.Type.SQL_DDL)
    }

    companion object {
        val dataPolicy = """

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
  ref: mydb.my_schema.gddemo
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
    fullname: 'my_database.my_schema.gddemo_public'
  field_transforms:
    - attribute:
        path_components:
          - email
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
