package com.getstrm.pace.processing_platforms.snowflake

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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SnowflakeViewGeneratorTest {

    private lateinit var underTest: SnowflakeViewGenerator

    @BeforeEach
    fun setUp() {
        underTest = SnowflakeViewGenerator(DataPolicy.getDefaultInstance())
    }

    @Test
    fun `principal check with multiple principals`() {
        // Given
        val principals = listOf("ANALYTICS", "MARKETING").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition!!.toSql() shouldBe
            "((IS_ROLE_IN_SESSION('ANALYTICS')) or (IS_ROLE_IN_SESSION('MARKETING')))"
    }

    @Test
    fun `principal check with a single principal`() {
        // Given
        val principals = listOf("ANALYTICS").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition!!.toSql() shouldBe "(IS_ROLE_IN_SESSION('ANALYTICS'))"
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
                .addAllPrincipals(listOf("ANALYTICS", "MARKETING").toPrincipals())
                .build()
        val otherFixed =
            DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
                .setFixed(
                    DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder()
                        .setValue("REDACTED EMAIL")
                )
                .addAllPrincipals(listOf("FRAUD_AND_RISK").toPrincipals())
                .build()
        val fallbackTransform =
            DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
                .setFixed(
                    DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder()
                        .setValue("stoelpoot")
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
            "case when ((IS_ROLE_IN_SESSION('ANALYTICS')) or (IS_ROLE_IN_SESSION('MARKETING'))) then '****' when (IS_ROLE_IN_SESSION('FRAUD_AND_RISK')) then 'REDACTED EMAIL' " +
                "else 'stoelpoot' end \"email\""
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
            "case when (IS_ROLE_IN_SESSION('fraud_and_risk')) then true when ((IS_ROLE_IN_SESSION('analytics')) " +
                "or (IS_ROLE_IN_SESSION('marketing'))) then age > 18 else false end"
    }

    @Test
    fun `retention to condition`() {
        // Given
        val retention =
            RetentionFilter.newBuilder()
                .setField(DataPolicy.Field.newBuilder().addNameParts("timestamp"))
                .addAllConditions(
                    listOf(
                        RetentionFilter.Condition.newBuilder()
                            .addPrincipals(DataPolicy.Principal.newBuilder().setGroup("MARKETING"))
                            .setPeriod(RetentionFilter.Period.newBuilder().setDays(5))
                            .build(),
                        RetentionFilter.Condition.newBuilder()
                            .addPrincipals(
                                DataPolicy.Principal.newBuilder().setGroup("FRAUD_AND_RISK")
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
            case when (IS_ROLE_IN_SESSION('MARKETING')) then dateadd(day, 5, timestamp) > current_timestamp when (IS_ROLE_IN_SESSION('FRAUD_AND_RISK')) then true else dateadd(day, 10, timestamp) > current_timestamp end
        """
                .trimIndent()
    }

    @Test
    fun `full sql view statement with single retention`() {
        // Given
        val viewGenerator =
            SnowflakeViewGenerator(singleRetentionPolicy) { withRenderFormatted(true) }
        // When

        // Then
        viewGenerator.toDynamicViewSQL().sql shouldBe
            """create or replace view public.demo_view
as
select
  ts,
  userid,
  transactionamount
from public.demo_tokenized
where (
  case
    when (IS_ROLE_IN_SESSION('fraud_and_risk')) then true
    else transactionamount < 10
  end
  and case
    when (IS_ROLE_IN_SESSION('marketing')) then dateadd(day, 5, ts) > current_timestamp
    when (IS_ROLE_IN_SESSION('fraud_and_risk')) then true
    else dateadd(day, 10, ts) > current_timestamp
  end
);
grant SELECT on public.demo_view to fraud_and_risk;
grant SELECT on public.demo_view to marketing;"""
    }

    @Test
    fun `full sql view statement with multiple retentions`() {
        // Given
        val viewGenerator =
            SnowflakeViewGenerator(multipleRetentionPolicy) { withRenderFormatted(true) }
        // When

        // Then
        viewGenerator.toDynamicViewSQL().sql shouldBe
            """create or replace view public.demo_view
as
select
  ts,
  validThrough,
  userid,
  transactionamount
from public.demo_tokenized
where (
  case
    when (IS_ROLE_IN_SESSION('fraud_and_risk')) then true
    else transactionamount < 10
  end
  and case
    when (IS_ROLE_IN_SESSION('marketing')) then dateadd(day, 5, ts) > current_timestamp
    when (IS_ROLE_IN_SESSION('fraud_and_risk')) then true
    else dateadd(day, 10, ts) > current_timestamp
  end
  and case
    when (IS_ROLE_IN_SESSION('fraud_and_risk')) then dateadd(day, 365, validThrough) > current_timestamp
    else dateadd(day, 0, validThrough) > current_timestamp
  end
);
grant SELECT on public.demo_view to fraud_and_risk;
grant SELECT on public.demo_view to marketing;"""
    }

    @Test
    fun `transform test various transforms`() {
        // Given
        underTest = SnowflakeViewGenerator(dataPolicy) { withRenderFormatted(true) }
        underTest
            .toDynamicViewSQL()
            .sql
            .shouldBe(
                """create or replace view my_database.my_schema.gddemo_public
as
select
  transactionId,
  case
    when (IS_ROLE_IN_SESSION('FRAUD_AND_RISK')) then userId
    else hash(1234, 'userId')
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
  case when brand = 'MacBook' then 'Apple' else 'Other' end brand,
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
        val dataPolicy =
            """
source:
  ref:
    integration_fqn: mydb.my_schema.gddemo
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
      ref: 
        integration_fqn: my_database.my_schema.gddemo_public
      type: SQL_VIEW
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
              replacement: "****$1"
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
              statement: "case when brand = 'MacBook' then 'Apple' else 'Other' end"
    filters:
      - generic_filter:
          conditions:
            - principals:
                - group: FRAUD_AND_RISK
              condition: "true"
            - principals: []
              condition: "age > 18"
      - generic_filter:
          conditions:
            - principals:
                - group: MARKETING
              condition: "userId in ('1', '2', '3', '4')"
            - principals: []
              condition: "true"
      - generic_filter:
          conditions:
            - principals: []
              condition: "transactionAmount < 10"
metadata: 
  title: "Data Policy for Pace BigQuery Demo Dataset"
  description: "Pace Demo Dataset"
  version: 10
  create_time: "2023-09-26T16:33:51.150Z"
  update_time: "2023-09-26T16:33:51.150Z"
              """
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
            - principals: [ {group: fraud_and_risk} ]
              period:
                days: "365"
            - principals: []
              period:
                days: "0"
            """
                .trimIndent()
                .toProto<DataPolicy>()
    }
}
