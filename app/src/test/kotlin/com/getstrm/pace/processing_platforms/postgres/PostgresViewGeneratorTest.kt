package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.GenericFilter
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.RetentionFilter
import com.getstrm.pace.namedField
import com.getstrm.pace.toPrincipals
import com.getstrm.pace.toSql
import com.getstrm.pace.util.toProto
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class PostgresViewGeneratorTest {
    private val underTest = PostgresViewGenerator(dataPolicy)

    @Test
    fun `principal check with multiple principals`() {
        // Given
        val principals = listOf("analytics", "marketing").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition!!.toSql() shouldBe
            "(('analytics' IN ( SELECT rolname FROM user_groups )) or ('marketing' IN ( SELECT rolname FROM user_groups )))"
    }

    @Test
    fun `principal check with a single principal`() {
        // Given
        val principals = listOf("analytics").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition!!.toSql() shouldBe "('analytics' IN ( SELECT rolname FROM user_groups ))"
    }

    @Test
    fun `principal check without any principals`() {
        // Given
        val principals = emptyList<DataPolicy.Principal>()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition.shouldBeNull()
    }

    @Test
    fun `field transform with a few transforms`() {
        // Given
        val field = namedField("email", "string")
        val fixed =
            DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
                .setFixed(
                    DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****")
                )
                .addAllPrincipals(listOf("analytics", "marketing").toPrincipals())
                .build()
        val otherFixed =
            DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
                .setFixed(
                    DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder()
                        .setValue("REDACTED EMAIL")
                )
                .addAllPrincipals(listOf("fraud_and_risk").toPrincipals())
                .build()
        val fallbackTransform =
            DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
                .setFixed(
                    DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder()
                        .setValue("fixed-value")
                )
                .build()
        val fieldTransform =
            DataPolicy.RuleSet.FieldTransform.newBuilder()
                .setField(field)
                .addAllTransforms(listOf(fixed, otherFixed, fallbackTransform))
                .build()

        // When
        val jooqField = underTest.toJooqField(field, fieldTransform)

        // Then
        jooqField.toSql() shouldBe
            "case when (('analytics' IN ( SELECT rolname FROM user_groups )) or ('marketing' IN ( SELECT rolname FROM user_groups ))) then '****' when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then 'REDACTED EMAIL' else 'fixed-value' end \"email\""
    }

    @Test
    fun `generic row filter to condition`() {
        // Given
        val filter =
            DataPolicy.RuleSet.Filter.newBuilder()
                .setGenericFilter(
                    GenericFilter.newBuilder()
                        .addAllConditions(
                            listOf(
                                GenericFilter.Condition.newBuilder()
                                    .addAllPrincipals(listOf("fraud_and_risk").toPrincipals())
                                    .setCondition("true")
                                    .build(),
                                GenericFilter.Condition.newBuilder()
                                    .addAllPrincipals(
                                        listOf("analytics", "marketing").toPrincipals()
                                    )
                                    .setCondition("age > 18")
                                    .build(),
                                GenericFilter.Condition.newBuilder().setCondition("false").build()
                            )
                        )
                )
                .build()

        // When
        val condition = underTest.toCondition(filter.genericFilter)

        // Then
        condition.toSql() shouldBe
            "case when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true when (('analytics' IN ( SELECT rolname FROM user_groups )) or ('marketing' IN ( SELECT rolname FROM user_groups ))) then age > 18 else false end"
    }

    @Test
    fun `single retention with single condition`() {
        // Given
        val retention =
            RetentionFilter.newBuilder()
                .setField(DataPolicy.Field.newBuilder().addNameParts("timestamp"))
                .addConditions(
                    RetentionFilter.Condition.newBuilder()
                        .addPrincipals(DataPolicy.Principal.getDefaultInstance())
                        .setPeriod(RetentionFilter.Period.newBuilder().setDays(10))
                        .build()
                )
                .build()

        // When
        val condition = underTest.toCondition(retention)

        // Then
        condition.toSql() shouldBe "dateadd(day, 10, \"timestamp\") > current_timestamp"
    }

    @Test
    fun `single retention to condition`() {
        // Given
        val retention =
            RetentionFilter.newBuilder()
                .setField(DataPolicy.Field.newBuilder().addNameParts("timestamp"))
                .addAllConditions(
                    listOf(
                        RetentionFilter.Condition.newBuilder()
                            .addPrincipals(DataPolicy.Principal.newBuilder().setGroup("marketing"))
                            .setPeriod(RetentionFilter.Period.newBuilder().setDays(5))
                            .build(),
                        RetentionFilter.Condition.newBuilder()
                            .addPrincipals(
                                DataPolicy.Principal.newBuilder().setGroup("fraud_and_risk")
                            )
                            .build(),
                        RetentionFilter.Condition.newBuilder()
                            .addPrincipals(DataPolicy.Principal.getDefaultInstance())
                            .setPeriod(RetentionFilter.Period.newBuilder().setDays(10))
                            .build()
                    )
                )
                .build()

        // When
        val condition = underTest.toCondition(retention)

        // Then
        condition.toSql() shouldBe
            """
case when ('marketing' IN ( SELECT rolname FROM user_groups )) then dateadd(day, 5, "timestamp") > current_timestamp when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true else dateadd(day, 10, "timestamp") > current_timestamp end"""
                .trimIndent()
    }

    @Test
    fun `full sql view statement with multiple retentions`() {
        // Given
        val viewGenerator =
            PostgresViewGenerator(multipleRetentionPolicy) { withRenderFormatted(true) }
        // When

        // Then
        viewGenerator.toDynamicViewSQL().sql shouldBe
            """create or replace view "public"."demo_view"
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
  "ts",
  "validThrough",
  "userid",
  "transactionamount"
from "public"."demo_tokenized"
where (
  case
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true
    else transactionamount < 10
  end
  and case
    when ('marketing' IN ( SELECT rolname FROM user_groups )) then ("ts" + 5 * interval '1 day') > current_timestamp
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true
    else ("ts" + 10 * interval '1 day') > current_timestamp
  end
  and ("validThrough" + 365 * interval '1 day') > current_timestamp
);
grant SELECT on public.demo_view to "fraud_and_risk";
grant SELECT on public.demo_view to "marketing";"""
    }

    @Test
    fun `full sql view statement with single retention`() {
        // Given
        val viewGenerator =
            PostgresViewGenerator(singleRetentionPolicy) { withRenderFormatted(true) }
        // When

        // Then
        viewGenerator.toDynamicViewSQL().sql shouldBe
            """create or replace view "public"."demo_view"
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
  "ts",
  "userid",
  "transactionamount"
from "public"."demo_tokenized"
where (
  case
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true
    else transactionamount < 10
  end
  and case
    when ('marketing' IN ( SELECT rolname FROM user_groups )) then ("ts" + 5 * interval '1 day') > current_timestamp
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true
    else ("ts" + 10 * interval '1 day') > current_timestamp
  end
);
grant SELECT on public.demo_view to "fraud_and_risk";
grant SELECT on public.demo_view to "marketing";"""
    }

    @Test
    fun `full SQL view statement with a single detokenize join`() {
        // Given
        val viewGenerator =
            PostgresViewGenerator(singleDetokenizePolicy) { withRenderFormatted(true) }
        viewGenerator.toDynamicViewSQL().sql shouldBe
            """create or replace view "public"."demo_view"
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
  "transactionid",
  case
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then coalesce("tokens"."userid_tokens"."userid", "public"."demo_tokenized"."userid")
    else "userid"
  end as userid,
  "transactionamount"
from "public"."demo_tokenized"
  left outer join "tokens"."userid_tokens"
    on ("public"."demo_tokenized".userid = "tokens"."userid_tokens".token)
where case
  when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true
  else transactionamount < 10
end;
grant SELECT on public.demo_view to "fraud_and_risk";"""
    }

    @Test
    fun `full SQL view statement with multiple detokenize joins on two tables`() {
        // Given
        val viewGenerator =
            PostgresViewGenerator(multiDetokenizePolicy) { withRenderFormatted(true) }
        viewGenerator.toDynamicViewSQL().sql shouldBe
            """create or replace view "public"."demo_view"
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
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then coalesce("tokens"."transactionid_tokens"."transactionid", "public"."demo_tokenized"."transactionid")
    else "transactionid"
  end as transactionid,
  case
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then coalesce("tokens"."userid_tokens"."userid", "public"."demo_tokenized"."userid")
    else "userid"
  end as userid,
  "transactionamount"
from "public"."demo_tokenized"
  left outer join "tokens"."userid_tokens"
    on ("public"."demo_tokenized".userid = "tokens"."userid_tokens".token)
  left outer join "tokens"."transactionid_tokens"
    on ("public"."demo_tokenized".transactionid = "tokens"."transactionid_tokens".token)
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
        viewGenerator
            .toDynamicViewSQL()
            .sql
            .shouldBe(
                """create or replace view "public"."demo_view"
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
  "transactionid",
  case
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then "userid"
    else 123
  end as userid,
  case
    when ('marketing' IN ( SELECT rolname FROM user_groups )) then regexp_replace(email, '^.*(@.*)${'$'}', '****\1', 'g')
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then "email"
    else '****'
  end as email,
  "age",
  CASE WHEN brand = 'blonde' THEN 'fair' ELSE 'dark' END as brand,
  case
    when ('marketing' IN ( SELECT rolname FROM user_groups )) then round(
      cast(avg(cast("transactionamount" as decimal)) over(partition by "brand") as numeric),
      2
    )
    when ('sales' IN ( SELECT rolname FROM user_groups )) then sum("transactionamount") over(partition by
      "brand",
      "age"
    )
    else round(
      cast(avg(cast("transactionamount" as float64)) over() as numeric),
      2
    )
  end as transactionamount
from "public"."demo"
where (
  case
    when ('fraud_and_risk' IN ( SELECT rolname FROM user_groups )) then true
    else age > 8
  end
  and transactionamount < 10
);
grant SELECT on public.demo_view to "fraud_and_risk";
grant SELECT on public.demo_view to "marketing";
grant SELECT on public.demo_view to "sales";"""
            )
    }

    companion object {
        @Language("yaml")
        val dataPolicy =
            """
metadata:
  description: ""
  version: 5
  title: public.demo
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
  ref:
    integration_fqn: public.demo
    platform:
      id: platform-id
      platform_type: POSTGRES
rule_sets:
  - target:
      ref: 
        integration_fqn: public.demo_view
    filters:
      - generic_filter:
          conditions:
            - principals: [ {group: fraud_and_risk} ]
              condition: "true"
            - principals: []
              condition: "age > 8"
      - generic_filter:
          conditions:
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
              value: "123"
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
      - field:
          name_parts: [ transactionamount ]
        transforms:
          - principals: [ {group: marketing} ]
            aggregation:
              avg:
                precision: 2
              partition_by:
                - name_parts: [ brand ]
          - principals: [ {group: sales} ]
            aggregation:
              sum: {}
              partition_by:
                - name_parts: [ brand ]
                - name_parts: [ age ]
          - principals: []
            aggregation:
              avg:
                precision: 2
                cast_to: "float64"
            """
                .trimIndent()
                .toProto<DataPolicy>()

        @Language("yaml")
        val singleDetokenizePolicy =
            """
metadata:
  description: ""
  version: 1
  title: public.demo
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
  ref:
    integration_fqn: public.demo_tokenized
    platform:
      id: platform-id
      platform_type: POSTGRES
rule_sets:
  - target:
      ref: 
        integration_fqn: public.demo_view
    filters:
      - generic_filter:
          conditions:
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

            """
                .trimIndent()
                .toProto<DataPolicy>()

        @Language("yaml")
        val multiDetokenizePolicy =
            """

metadata:
  description: ""
  version: 1
  title: public.demo
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
  ref:
    integration_fqn: public.demo_tokenized
    platform:
      id: platform-id
      platform_type: POSTGRES
rule_sets:
  - target:
      ref: 
        integration_fqn: public.demo_view
    filters:
      - generic_filter:
          conditions:
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
            """
                .trimIndent()
                .toProto<DataPolicy>()

        @Language("yaml")
        val singleRetentionPolicy =
            """
metadata:
  description: ""
  version: 1
  title: public.demo
source:
  fields:
    - name_parts:
        - ts
      required: true
      type: timestamp
    - name_parts:
        - userid
      required: true
      type: integer
    - name_parts:
        - transactionamount
      required: true
      type: integer
  ref:
    integration_fqn: public.demo_tokenized
    platform:
      id: platform-id
      platform_type: POSTGRES
rule_sets:
  - target:
      ref: 
        integration_fqn: public.demo_view
    filters:
      - generic_filter:
          conditions:
            - principals: [ {group: fraud_and_risk} ]
              condition: "true"
            - principals : []
              condition: "transactionamount < 10"
      - retention_filter:
          field:
            name_parts:
              - ts
            required: true
            type: timestamp
          conditions:
            - principals: [ {group: marketing} ]
              period:
                days: "5"
            - principals: [ {group: fraud_and_risk} ]
            - principals: []
              period:
                days: "10"

            """
                .trimIndent()
                .toProto<DataPolicy>()

        @Language("yaml")
        val multipleRetentionPolicy =
            """
metadata:
  description: ""
  version: 1
  title: public.demo
source:
  fields:
    - name_parts:
        - ts
      required: true
      type: timestamp
    - name_parts:
        - validThrough
      required: true
      type: timestamp
    - name_parts:
        - userid
      required: true
      type: integer
    - name_parts:
        - transactionamount
      required: true
      type: integer
  ref:
    integration_fqn: public.demo_tokenized
    platform:
      id: platform-id
      platform_type: POSTGRES
rule_sets:
  - target:
      ref: 
        integration_fqn: public.demo_view
    filters:
      - generic_filter:
          conditions:
            - principals: [ {group: fraud_and_risk} ]
              condition: "true"
            - principals : []
              condition: "transactionamount < 10"
      - retention_filter:
          field:
            name_parts:
              - ts
            required: true
            type: timestamp
          conditions:
            - principals: [ {group: marketing} ]
              period:
                days: "5"
            - principals: [ {group: fraud_and_risk} ]
            - principals: []
              period:
                days: "10"
      - retention_filter:
          field:
            name_parts:
              - validThrough
            required: true
            type: timestamp
          conditions:
            - principals: []
              period:
                days: "365"
            """
                .trimIndent()
                .toProto<DataPolicy>()
    }
}
