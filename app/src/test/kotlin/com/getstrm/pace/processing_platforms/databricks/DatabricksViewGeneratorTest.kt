package com.getstrm.pace.processing_platforms.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.GenericFilter
import com.getstrm.pace.namedField
import com.getstrm.pace.toPrincipal
import com.getstrm.pace.toPrincipals
import com.getstrm.pace.toSql
import com.getstrm.pace.util.toProto
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DatabricksViewGeneratorTest {

    private lateinit var underTest: DatabricksViewGenerator

    @BeforeEach
    fun setUp() {
        underTest = DatabricksViewGenerator(DataPolicy.getDefaultInstance())
    }

    @Test
    fun `principal check with multiple principals`() {
        // Given
        val principals = listOf("analytics", "marketing").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition!!.toSql() shouldBe
            "((is_account_group_member('analytics')) or (is_account_group_member('marketing')))"
    }

    @Test
    fun `principal check with a single principal`() {
        // Given
        val principals = listOf("analytics").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition!!.toSql() shouldBe "(is_account_group_member('analytics'))"
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
    fun `field transform with three transforms`() {
        // Given
        val attribute = namedField("email", "string")
        val fixed =
            DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
                .setFixed(
                    DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****")
                )
                .addAllPrincipals(listOf("marketing", "analytics").toPrincipals())
                .build()
        val otherFixed =
            DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
                .setFixed(
                    DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder()
                        .setValue("REDACTED EMAIL")
                )
                .addAllPrincipals(listOf("fraud-and-risk").toPrincipals())
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
                .setField(attribute)
                .addAllTransforms(listOf(fixed, otherFixed, fallbackTransform))
                .build()

        // When
        val field = underTest.toJooqField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe
            "case when ((is_account_group_member('marketing')) or (is_account_group_member('analytics'))) then '****' when (is_account_group_member('fraud-and-risk')) then " +
                "'REDACTED EMAIL' else 'stoelpoot' end \"email\""
    }

    @Test
    fun `field transform with two transforms`() {
        // Given
        val attribute = namedField("email", "string")
        val fixed =
            DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
                .setFixed(
                    DataPolicy.RuleSet.FieldTransform.Transform.Fixed.newBuilder().setValue("****")
                )
                .addPrincipals("analytics".toPrincipal())
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
                .setField(attribute)
                .addAllTransforms(listOf(fixed, fallbackTransform))
                .build()

        // When
        val field = underTest.toJooqField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe
            "case when (is_account_group_member('analytics')) then '****' else 'stoelpoot' end \"email\""
    }

    @Test
    fun `field transform with single transform`() {
        // Given
        val attribute = namedField("email")
        val fallbackTransform =
            DataPolicy.RuleSet.FieldTransform.Transform.newBuilder()
                .setRegexp(
                    DataPolicy.RuleSet.FieldTransform.Transform.Regexp.newBuilder()
                        .setRegexp("^.*(@.*)$")
                        .setReplacement("****$1")
                )
                .build()
        val fieldTransform =
            DataPolicy.RuleSet.FieldTransform.newBuilder()
                .setField(attribute)
                .addTransforms(fallbackTransform)
                .build()

        // When
        val field = underTest.toJooqField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe "regexp_replace(email, '^.*(@.*)\$', '****\$1') \"email\""
    }

    @Test
    fun `field with no transform`() {
        // Given
        val attribute = namedField("email")

        // When
        val field = underTest.toJooqField(attribute, null)

        // Then
        field shouldBe DSL.field("email")
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
                                    .addAllPrincipals(listOf("fraud-and-risk").toPrincipals())
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
            "case when (is_account_group_member('fraud-and-risk')) then true when ((is_account_group_member('analytics')) or (is_account_group_member('marketing'))) " +
                "then age > 18 else false end"
    }

    @Test
    fun `single retention to condition`() {
        // Given
        val retention =
            DataPolicy.RuleSet.Filter.RetentionFilter.newBuilder()
                .setField(DataPolicy.Field.newBuilder().addNameParts("timestamp"))
                .addAllConditions(
                    listOf(
                        DataPolicy.RuleSet.Filter.RetentionFilter.Condition.newBuilder()
                            .addPrincipals(DataPolicy.Principal.newBuilder().setGroup("marketing"))
                            .setPeriod(
                                DataPolicy.RuleSet.Filter.RetentionFilter.Period.newBuilder()
                                    .setDays(5)
                            )
                            .build(),
                        DataPolicy.RuleSet.Filter.RetentionFilter.Condition.newBuilder()
                            .addPrincipals(
                                DataPolicy.Principal.newBuilder().setGroup("fraud-and-risk")
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
        val condition = underTest.toCondition(retention)

        // Then
        condition.toSql() shouldBe
            """
            case when (is_account_group_member('marketing')) then dateadd(day, 5, timestamp) > current_timestamp when (is_account_group_member('fraud-and-risk')) then true else dateadd(day, 10, timestamp) > current_timestamp end"""
                .trimIndent()
    }

    @Test
    fun `full sql view statement with multiple retentions`() {
        // Given
        val viewGenerator =
            DatabricksViewGenerator(multipleRetentionPolicy) { withRenderFormatted(true) }
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
    when (is_account_group_member('fraud-and-risk')) then true
    else transactionamount < 10
  end
  and case
    when (is_account_group_member('marketing')) then dateadd(day, 5, ts) > current_timestamp
    when (is_account_group_member('fraud-and-risk')) then true
    else dateadd(day, 10, ts) > current_timestamp
  end
  and case
    when (is_account_group_member('fraud-and-risk')) then dateadd(day, 365, validThrough) > current_timestamp
    else dateadd(day, 0, validThrough) > current_timestamp
  end
);"""
    }

    @Test
    fun `full sql view statement with single retention`() {
        // Given
        val viewGenerator =
            DatabricksViewGenerator(singleRetentionPolicy) { withRenderFormatted(true) }
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
    when (is_account_group_member('fraud-and-risk')) then true
    else transactionamount < 10
  end
  and case
    when (is_account_group_member('marketing')) then dateadd(day, 5, ts) > current_timestamp
    when (is_account_group_member('fraud-and-risk')) then true
    else dateadd(day, 10, ts) > current_timestamp
  end
);"""
    }

    @Test
    fun `transform test various transforms`() {
        underTest = DatabricksViewGenerator(dataPolicy) { withRenderFormatted(true) }
        underTest
            .toDynamicViewSQL()
            .sql
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
  case when brand = 'MacBook' then 'Apple' else 'Other' end brand,
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
        val policyWithoutFilters =
            dataPolicy.toBuilder().apply { ruleSetsBuilderList.first().clearFilters() }.build()
        underTest = DatabricksViewGenerator(policyWithoutFilters) { withRenderFormatted(true) }
        underTest
            .toDynamicViewSQL()
            .sql
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
  case when brand = 'MacBook' then 'Apple' else 'Other' end brand,
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
        private val dataPolicy =
            """
source:
  ref:
    integration_fqn: mycatalog.my_schema.gddemo
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
      ref: 
        integration_fqn: my_catalog.my_schema.gddemo_public
      type: SQL_VIEW
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
              statement: "case when brand = 'MacBook' then 'Apple' else 'Other' end"
    filters:
      - generic_filter:
          conditions:
            - principals:
                - group: fraud-and-risk
              condition: "true"
            - principals: []
              condition: "age > 18"
      - generic_filter:
          conditions:
            - principals:
                - group: marketing
              condition: "userId in ('1', '2', '3', '4')"
            - principals: []
              condition: "true"
      - generic_filter:
          conditions:
            - principals: []
              condition: "transactionAmount < 10"
metadata: 
  title: "Data Policy for Pace Databricks Demo Dataset"
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
            - principals: [ {group: fraud-and-risk} ]
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
            - principals: [ {group: fraud-and-risk} ]
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
            - principals: [ {group: fraud-and-risk} ]
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
            - principals: [ {group: fraud-and-risk} ]
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
            - principals: [ {group: fraud-and-risk} ]
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
