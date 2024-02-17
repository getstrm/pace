package com.getstrm.pace.processing_platforms.h2

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.namedField
import com.getstrm.pace.processing_platforms.snowflake.SnowflakeViewGenerator
import com.getstrm.pace.toPrincipals
import com.getstrm.pace.toSql
import com.getstrm.pace.util.toProto
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class H2ViewGeneratorTest {

    private lateinit var underTest: H2ViewGenerator
    private val analyticsPrincipal = DataPolicy.Principal.newBuilder().setGroup("ANALYTICS").build()
    private val marketingPrincipal = DataPolicy.Principal.newBuilder().setGroup("MARKETING").build()
    private val fraudAndRiskPrincipal =
        DataPolicy.Principal.newBuilder().setGroup("FRAUD_AND_RISK").build()

    @BeforeEach
    fun setUp() {
        underTest = H2ViewGenerator(DataPolicy.getDefaultInstance(), null, "table_name_override")
    }

    @Test
    fun `principal check - no principal to apply`() {
        // Given
        val principals = listOf("ANALYTICS", "MARKETING").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition shouldBe DSL.falseCondition()
    }

    @Test
    fun `principal check - principal matches`() {
        // Given
        underTest = H2ViewGenerator(dataPolicy, marketingPrincipal, "source_override")
        val principals = listOf("ANALYTICS", "MARKETING").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition shouldBe DSL.trueCondition()
    }

    @Test
    fun `principal check - principal does not match`() {
        // Given
        underTest = H2ViewGenerator(dataPolicy, fraudAndRiskPrincipal, "source_override")
        val principals = listOf("ANALYTICS", "MARKETING").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition shouldBe DSL.falseCondition()
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
        underTest = H2ViewGenerator(dataPolicy, fraudAndRiskPrincipal, "source_override")
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
        val jooqField =
            underTest.toJooqField(field, fieldTransform, DataPolicy.Target.getDefaultInstance())

        // Then
        // Fixme: when calling toJooqField directly, there is additional escaping on the field
        // alias, for some reason.
        jooqField.toSql() shouldBe
            "case when false then '****' when true then 'REDACTED EMAIL' " +
            "else 'stoelpoot' end \"email\""
    }

    @Test
    fun `generic row filter to condition`() {
        // Given
        val filter =
            DataPolicy.RuleSet.Filter.newBuilder()
                .setGenericFilter(
                    DataPolicy.RuleSet.Filter.GenericFilter.newBuilder()
                        .addAllConditions(
                            listOf(
                                DataPolicy.RuleSet.Filter.GenericFilter.Condition.newBuilder()
                                    .addAllPrincipals(listOf("fraud_and_risk").toPrincipals())
                                    .setCondition("true")
                                    .build(),
                                DataPolicy.RuleSet.Filter.GenericFilter.Condition.newBuilder()
                                    .addAllPrincipals(
                                        listOf("analytics", "marketing").toPrincipals()
                                    )
                                    .setCondition("age > 18")
                                    .build(),
                                DataPolicy.RuleSet.Filter.GenericFilter.Condition.newBuilder()
                                    .setCondition("false")
                                    .build()
                            )
                        )
                )
                .build()

        // When
        val condition =
            underTest.toCondition(filter.genericFilter, DataPolicy.Target.getDefaultInstance())

        // Then
        condition.toSql() shouldBe
            "case when false then true when false then age > 18 else false end"
    }

    @Test
    fun `retention to condition`() {
        // Given
        val retention =
            DataPolicy.RuleSet.Filter.RetentionFilter.newBuilder()
                .setField(DataPolicy.Field.newBuilder().addNameParts("timestamp"))
                .addAllConditions(
                    listOf(
                        DataPolicy.RuleSet.Filter.RetentionFilter.Condition.newBuilder()
                            .addPrincipals(DataPolicy.Principal.newBuilder().setGroup("MARKETING"))
                            .setPeriod(
                                DataPolicy.RuleSet.Filter.RetentionFilter.Period.newBuilder()
                                    .setDays(5)
                            )
                            .build(),
                        DataPolicy.RuleSet.Filter.RetentionFilter.Condition.newBuilder()
                            .addPrincipals(
                                DataPolicy.Principal.newBuilder().setGroup("FRAUD_AND_RISK")
                            )
                            .build(),
                        DataPolicy.RuleSet.Filter.RetentionFilter.Condition.newBuilder()
                            .addPrincipals(DataPolicy.Principal.getDefaultInstance())
                            .setPeriod(
                                DataPolicy.RuleSet.Filter.RetentionFilter.Period.newBuilder()
                                    .setDays(10)
                            )
                            .build()
                    )
                )
                .build()

        // When
        val condition = underTest.toCondition(retention, DataPolicy.Target.getDefaultInstance())

        // Then
        condition.toSql() shouldBe
            """
            case when false then dateadd(day, 5, timestamp) > current_timestamp when false then true else dateadd(day, 10, timestamp) > current_timestamp end
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
    fun `transform test various transforms - analytics principal`() {
        // Given
        underTest =
            H2ViewGenerator(dataPolicy, analyticsPrincipal, "source_override") {
                withRenderFormatted(true)
            }
        underTest
            .toDynamicViewSQL()
            .sql
            .shouldBe(
                """create or replace view my_database.my_schema.gddemo_public
as
select
  transactionId,
  case
    when false then userId
    else hash(1234, 'userId')
  end userId,
  case
    when true then regexp_replace(email, '^.*(@.*)${'$'}', '****${'$'}1')
    when false then email
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
from source_override
where (
  case
    when false then true
    else age > 18
  end
  and case
    when false then userId in ('1', '2', '3', '4')
    else true
  end
  and transactionAmount < 10
);""",
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
