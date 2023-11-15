package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.toPrincipal
import com.getstrm.pace.toPrincipals
import com.getstrm.pace.toSql
import com.getstrm.pace.util.parseDataPolicy
import com.getstrm.pace.util.yaml2json
import com.google.rpc.BadRequest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PostgresViewGeneratorTest {
    private val underTest = PostgresViewGenerator(dataPolicy)

    @Test
    fun `fixed value transform with multiple principals`() {
        // Given
        val field = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("analytics", "marketing").toPrincipals())
            .build()

        // When
        val (condition, jooqField) = underTest.toCase(transform, field)

        // Then
        condition!!.toSql() shouldBe "(('analytics' IN ( SELECT rolname FROM user_groups )) or ('marketing' IN ( SELECT rolname FROM user_groups )))"
        jooqField.toSql() shouldBe "'****'"
    }

    @Test
    fun `fixed value transform with a single principal`() {
        // Given
        val field = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("analytics").toPrincipals())
            .build()

        // When
        val (condition, jooqField) = underTest.toCase(transform, field)

        // Then
        condition!!.toSql() shouldBe "('analytics' IN ( SELECT rolname FROM user_groups ))"
        jooqField.toSql() shouldBe "'****'"
    }

    @Test
    fun `fixed value transform without principals`() {
        // Given
        val field = DataPolicy.Field.newBuilder().addNameParts("email").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .build()

        // When
        val (condition, jooqField) = underTest.toCase(transform, field)

        // Then
        condition.shouldBeNull()
        jooqField.toSql() shouldBe "'****'"
    }

    @Test
    fun `test detokenize condition`() {
        // Given
        val field = DataPolicy.Field.newBuilder().addNameParts("user_id_token").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setDetokenize(
                DataPolicy.RuleSet.FieldTransform.Transform.Detokenize.newBuilder()
                    .setTokenSourceRef("public.user_id_tokens")
                    .setTokenField(DataPolicy.Field.newBuilder().addNameParts("token"))
                    .setValueField(DataPolicy.Field.newBuilder().addNameParts("user_id"))
            )
            .addPrincipals("analytics".toPrincipal())
            .build()

        // When
        val (condition, jooqField) = underTest.toCase(transform, field)

        // Then
        condition!!.toSql() shouldBe "('analytics' IN ( SELECT rolname FROM user_groups ))"
        jooqField.toSql() shouldBe "coalesce(public.user_id_tokens.user_id, public.demo.user_id_token)"
    }

    @Test
    fun `test detokenize condition without schema in token source ref`() {
        // Given
        val field = DataPolicy.Field.newBuilder().addNameParts("user_id_token").setType("string").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setDetokenize(
                DataPolicy.RuleSet.FieldTransform.Transform.Detokenize.newBuilder()
                    .setTokenSourceRef("user_id_tokens")
                    .setTokenField(DataPolicy.Field.newBuilder().addNameParts("token"))
                    .setValueField(DataPolicy.Field.newBuilder().addNameParts("user_id"))
            )
            .addPrincipals("analytics".toPrincipal())
            .build()

        // When
        val (condition, jooqField) = underTest.toCase(transform, field)

        // Then
        condition!!.toSql() shouldBe "('analytics' IN ( SELECT rolname FROM user_groups ))"
        jooqField.toSql() shouldBe "coalesce(user_id_tokens.user_id, public.demo.user_id_token)"
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
        val jooqField = underTest.toJooqField(field, fieldTransform)

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
    fun `fixed value data type should match the attribute data type`() {
        // Given an attribute and a transform
        val field = DataPolicy.Field.newBuilder().addNameParts("age").setType("integer").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setFixed(DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .build()

        // When the transform is rendered as case
        val exception = assertThrows<BadRequestException> { underTest.toCase(transform, field) }

        // Then
        exception.code shouldBe BadRequestException.Code.INVALID_ARGUMENT
        exception.badRequest shouldBe BadRequest.newBuilder()
            .addAllFieldViolations(
                listOf(
                    BadRequest.FieldViolation.newBuilder()
                        .setField("dataPolicy.ruleSetsList.fieldTransformsList.fixed")
                        .setDescription("Data type of fixed value provided for field age does not match the data type of the field")
                        .build()
                )
            )
            .build()
    }

    @Test
    fun `numeric rounding - ceil`() {
        // Given
        val field = DataPolicy.Field.newBuilder().addNameParts("transactionamount").setType("integer").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setNumericRounding(
                DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.newBuilder()
                    .setCeil(
                        DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Ceil.newBuilder().setDivisor(200f)
                    )
            )
            .build()

        // When
        val (_, jooqField) = underTest.toCase(transform, field)

        // Then
        jooqField.toSql() shouldBe "(ceil((transactionamount / 2E2)) * 2E2)"
    }

    @Test
    fun `numeric rounding - floor`() {
        // Given
        val field = DataPolicy.Field.newBuilder().addNameParts("transactionamount").setType("float").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setNumericRounding(
                DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.newBuilder()
                    .setFloor(
                        DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Floor.newBuilder().setDivisor(-200f)
                    )
            )
            .build()

        // When
        val (_, jooqField) = underTest.toCase(transform, field)

        // Then
        jooqField.toSql() shouldBe "(floor((transactionamount / -2E2)) * -2E2)"
    }

    @Test
    fun `numeric rounding - round`() {
        // Given
        val field = DataPolicy.Field.newBuilder().addNameParts("transactionamount").setType("float").build()
        val transform = DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
            .setNumericRounding(
                DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.newBuilder()
                    .setRound(
                        DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding.Round.newBuilder().setPrecision(-1)
                    )
            )
            .build()

        // When
        val (_, jooqField) = underTest.toCase(transform, field)

        // Then
        jooqField.toSql() shouldBe "round(transactionamount, -1)"
    }

    @Test
    fun `full SQL view statement with a single detokenize join`() {
        // Given
        val viewGenerator = PostgresViewGenerator(singleDetokenizePolicy) { withRenderFormatted(true) }
        viewGenerator.toDynamicViewSQL() shouldBe
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
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then coalesce(tokens.userid_tokens.userid, public.demo_tokenized.userid)
    else userid
  end as userid,
  transactionamount
from public.demo_tokenized
  left outer join tokens.userid_tokens
    on (public.demo_tokenized.userid = tokens.userid_tokens.token)
where case
  when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true
  else transactionamount < 10
end;
grant SELECT on public.demo_view to "fraud_and_risk";"""
    }

    @Test
    fun `full SQL view statement with multiple detokenize joins on two tables`() {
        // Given
        val viewGenerator = PostgresViewGenerator(multiDetokenizePolicy) { withRenderFormatted(true) }
        viewGenerator.toDynamicViewSQL() shouldBe
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
  case
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then coalesce(tokens.transactionid_tokens.transactionid, public.demo_tokenized.transactionid)
    else transactionid
  end as transactionid,
  case
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then coalesce(tokens.userid_tokens.userid, public.demo_tokenized.userid)
    else userid
  end as userid,
  transactionamount
from public.demo_tokenized
  left outer join tokens.userid_tokens
    on (public.demo_tokenized.userid = tokens.userid_tokens.token)
  left outer join tokens.transactionid_tokens
    on (public.demo_tokenized.transactionid = tokens.transactionid_tokens.token)
where case
  when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true
  else transactionamount < 10
end;
grant SELECT on public.demo_view to "fraud_and_risk";"""
    }

    @Test
    fun `full SQL view statement with multiple transforms`() {
        // Given
        val viewGenerator = PostgresViewGenerator(dataPolicy) { withRenderFormatted(true) }
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
    else 123
  end as userid,
  case
    when ('marketing' IN ( SELECT rolname FROM user_groups )) then regexp_replace(email, '^.*(@.*)${'$'}', '****\1', 'g')
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then email
    else '****'
  end as email,
  age,
  CASE WHEN brand = 'blonde' THEN 'fair' ELSE 'dark' END as brand,
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
              platform_type: POSTGRES
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
                          value: 123
                  - field:
                      name_parts: [ email ]
                    transforms:
                      - principals: [ {group: marketing} ]
                        regexp:
                          regexp: "^.*(@.*)${'$'}"
                          replacement: '****$1'
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

        @Language("yaml")
        val singleDetokenizePolicy = """
            metadata:
              description: ""
              version: 1
              title: public.demo
            platform:
              id: platform-id
              platform_type: POSTGRES
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
                    - transactionamount
                  required: true
                  type: integer
              ref: public.demo_tokenized
            rule_sets:
              - target:
                  fullname: public.demo_view
                filters:
                  - conditions:
                      - principals: [ {group: fraud_and_risk} ]
                        condition: "true"
                      - principals : []
                        condition: "transactionamount < 10"
                field_transforms:
                  - field:
                      name_parts: [ userid ]
                    transforms:
                      - principals: [ {group: fraud_and_risk} ]
                        detokenize:
                          token_source_ref: tokens.userid_tokens
                          token_field:
                            name_parts: [ token ]
                          value_field:
                            name_parts: [ userid ]
                      - principals: []
                        identity: {}
        """.trimIndent().yaml2json().parseDataPolicy()

        @Language("yaml")
        val multiDetokenizePolicy = """
            metadata:
              description: ""
              version: 1
              title: public.demo
            platform:
              id: platform-id
              platform_type: POSTGRES
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
                    - transactionamount
                  required: true
                  type: integer
              ref: public.demo_tokenized
            rule_sets:
              - target:
                  fullname: public.demo_view
                filters:
                  - conditions:
                      - principals: [ {group: fraud_and_risk} ]
                        condition: "true"
                      - principals : []
                        condition: "transactionamount < 10"
                field_transforms:
                  - field:
                      name_parts: [ userid ]
                    transforms:
                      - principals: [ {group: fraud_and_risk} ]
                        detokenize:
                          token_source_ref: tokens.userid_tokens
                          token_field:
                            name_parts: [ token ]
                          value_field:
                            name_parts: [ userid ]
                      - principals: []
                        identity: {}
                  - field:
                      name_parts: [ transactionid ]
                    transforms:
                      - principals: [ {group: fraud_and_risk} ]
                        detokenize:
                          token_source_ref: tokens.transactionid_tokens
                          token_field:
                            name_parts: [ token ]
                          value_field:
                            name_parts: [ transactionid ]
                      - principals: []
                        identity: {}

        """.trimIndent().yaml2json().parseDataPolicy()
    }
}
