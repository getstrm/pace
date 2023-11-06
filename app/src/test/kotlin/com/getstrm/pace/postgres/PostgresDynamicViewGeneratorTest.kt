package com.getstrm.pace.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.toPrincipals
import com.getstrm.pace.toSql
import com.getstrm.pace.util.parseDataPolicy
import com.getstrm.pace.util.yaml2json
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class PostgresDynamicViewGeneratorTest {
    private val underTest = PostgresDynamicViewGenerator(dataPolicy)

    @Test
    fun `fixed value transform with multiple principals`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("analytics", "marketing").toPrincipals())
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition!!.toSql() shouldBe "(('analytics' IN ( SELECT rolname FROM user_groups )) or ('marketing' IN ( SELECT rolname FROM user_groups )))"
        field.toSql() shouldBe "'****'"
    }

    @Test
    fun `fixed value transform with a single principal`() {
        // Given
        val attribute = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("analytics").toPrincipals())
            .build()

        // When
        val (condition, field) = underTest.toCase(transform, attribute)

        // Then
        condition!!.toSql() shouldBe "('analytics' IN ( SELECT rolname FROM user_groups ))"
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
        val fixed = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("analytics", "marketing").toPrincipals())
            .build()
        val otherFixed = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("REDACTED EMAIL"))
            .addAllPrincipals(listOf("fraud_and_risk").toPrincipals())
            .build()
        val fallbackTransform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("fixed-value"))
            .build()
        val fieldTransform = DataPolicy.RuleSet.FieldTransform.newBuilder()
            .setField(field)
            .addAllTransforms(listOf(fixed, otherFixed, fallbackTransform))
            .build()

        // When
        val jooqField = underTest.toField(field, fieldTransform)

        // Then
        jooqField.toSql() shouldBe "case when (('analytics' IN ( SELECT rolname FROM user_groups )) or ('marketing' IN ( SELECT rolname FROM user_groups ))) then '****' when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then 'REDACTED EMAIL' else 'fixed-value' end \"email\""
    }

    @Test
    fun `row filter to condition`() {
        // Given
        val filter = DataPolicy.RuleSet.Filter.newBuilder()
            .addAllConditions(
                listOf(
                    DataPolicy.RuleSet.Filter.Condition.newBuilder()
                        .addAllPrincipals(listOf("fraud_and_risk").toPrincipals())
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
        condition.toSql() shouldBe "case when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true when (('analytics' IN ( SELECT rolname FROM user_groups )) or ('marketing' IN ( SELECT rolname FROM user_groups ))) then age > 18 else false end"
    }

    @Test
    fun `transform test all transforms`() {
        // Given
        val viewGenerator = PostgresDynamicViewGenerator(dataPolicy) { withRenderFormatted(true) }
        viewGenerator.toDynamicViewSQL()
            .shouldBe(
                """create or replace view public.demo_view
as
with
  user_groups as (
    select rolname
    from pg_roles
    where (
      rolcanlogin = false
      and pg_has_role(
        session_user,
        oid,
        'member'
      )
    )
  )
select
  transactionid,
  case
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then userid
    else null
  end userid,
  case
    when ('marketing' IN ( SELECT rolname FROM user_groups )) then regexp_replace(email, '^.*(@.*)${'$'}', '****\1')
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then email
    else '****'
  end email,
  age,
  CASE WHEN brand = 'blonde' THEN 'fair' ELSE 'dark' END brand,
  transactionamount
from public.demo
where (
  case
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true
    else age > 8
  end
  and transactionamount < 10
);
grant SELECT on public.demo_view to "fraud_and_risk";
grant SELECT on public.demo_view to "marketing";"""
            )
    }

    companion object {
        @Language("yaml")
        val dataPolicy = """
            metadata:
              description: ""
              version: 5
              title: public.demo
            platform:
              id: platform-id
              platform_type: 4
            source:
              fields:
                - name_parts:
                    - transactionid
                  required: true
                  type: integer
                - name_parts:
                    - userid
                  required: true
                  type: integer
                - name_parts:
                    - email
                  required: true
                  type: varchar
                - name_parts:
                    - age
                  required: true
                  type: integer
                - name_parts:
                    - brand
                  required: true
                  type: varchar
                - name_parts:
                    - transactionamount
                  required: true
                  type: integer
              ref: public.demo
            rule_sets:
              - target:
                  fullname: public.demo_view
                filters:
                  - conditions:
                      - principals: [ {group: fraud_and_risk} ]
                        condition: "true"
                      - principals: []
                        condition: "age > 8"
                  - conditions:
                      - principals : []
                        condition: "transactionamount < 10"
                field_transforms:
                  - field:
                      name_parts: [ userid ]
                    transforms:
                      - principals: [ {group: fraud_and_risk} ]
                        identity: {}
                      - principals: []
                        fixed:
                          value: "****"
                  - field:
                      name_parts: [ email ]
                    transforms:
                      - principals: [ {group: marketing} ]
                        regexp:
                          regexp: "^.*(@.*)${'$'}"
                          replacement: "****\\1"
                      - principals: [ {group: fraud_and_risk} ]
                        identity: {}
                      - principals: []
                        fixed:
                          value: "****"
                  - field:
                      name_parts: [ brand ]
                    transforms:
                      - principals: []
                        sql_statement:
                          statement: "CASE WHEN brand = 'blonde' THEN 'fair' ELSE 'dark' END"
        """.trimIndent().yaml2json().parseDataPolicy()
    }
}
