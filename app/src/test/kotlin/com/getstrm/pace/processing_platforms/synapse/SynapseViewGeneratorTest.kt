package com.getstrm.pace.processing_platforms.synapse

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.Principal
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.GenericFilter
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.RetentionFilter
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.namedField
import com.getstrm.pace.processing_platforms.databricks.SynapseViewGenerator
import com.getstrm.pace.toPrincipal
import com.getstrm.pace.toPrincipals
import com.getstrm.pace.toSql
import com.getstrm.pace.util.toProto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SynapseViewGeneratorTest {

    private lateinit var underTest: SynapseViewGenerator

    @BeforeEach
    fun setUp() {
        underTest = SynapseViewGenerator(DataPolicy.getDefaultInstance())
    }

    @Test
    fun `principal check with multiple principals`() {
        // Given
        val principals = listOf("analytics", "marketing").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition!!.toSql() shouldBe "((IS_ROLEMEMBER('analytics')=1) or (IS_ROLEMEMBER('marketing')=1))"
    }

    @Test
    fun `principal check with a single principal`() {
        // Given
        val principals = listOf("analytics").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition!!.toSql() shouldBe "(IS_ROLEMEMBER('analytics')=1)"
    }

    @Test
    fun `principal check without any principals`() {
        // Given
        val principals = emptyList<Principal>()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition.shouldBeNull()
    }

    @Test
    fun `field transform with three transforms`() {
        // Given
        val attribute = namedField("email", "string")
        val fixed = FieldTransform.Transform.newBuilder()
            .setFixed(FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addAllPrincipals(listOf("marketing", "analytics").toPrincipals())
            .build()
        val otherFixed = FieldTransform.Transform.newBuilder()
            .setFixed(FieldTransform.Transform.Fixed.newBuilder().setValue("REDACTED EMAIL"))
            .addAllPrincipals(listOf("fraud_and_risk").toPrincipals())
            .build()
        val fallbackTransform = FieldTransform.Transform.newBuilder()
            .setFixed(FieldTransform.Transform.Fixed.newBuilder().setValue("stoelpoot"))
            .build()
        val fieldTransform = FieldTransform.newBuilder()
            .setField(attribute)
            .addAllTransforms(listOf(fixed, otherFixed, fallbackTransform))
            .build()

        // When
        val field = underTest.toJooqField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe "case when ((IS_ROLEMEMBER('marketing')=1) or (IS_ROLEMEMBER('analytics')=1)) then '****' when (IS_ROLEMEMBER('fraud_and_risk')=1) then " +
                "'REDACTED EMAIL' else 'stoelpoot' end \"email\""
    }

    @Test
    fun `field transform with two transforms`() {
        // Given
        val attribute = namedField("email", "string")
        val fixed = FieldTransform.Transform.newBuilder()
            .setFixed(FieldTransform.Transform.Fixed.newBuilder().setValue("****"))
            .addPrincipals("analytics".toPrincipal())
            .build()
        val fallbackTransform = FieldTransform.Transform.newBuilder()
            .setFixed(FieldTransform.Transform.Fixed.newBuilder().setValue("stoelpoot"))
            .build()
        val fieldTransform = FieldTransform.newBuilder()
            .setField(attribute)
            .addAllTransforms(listOf(fixed, fallbackTransform))
            .build()

        // When
        val field = underTest.toJooqField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe "case when (IS_ROLEMEMBER('analytics')=1) then '****' else 'stoelpoot' end \"email\""
    }

    @Test
    fun `field transform with single transform`() {
        // Given
        val attribute = namedField("email")
        val fallbackTransform = FieldTransform.Transform.newBuilder()
            .setFixed(
                FieldTransform.Transform.Fixed.newBuilder().setValue("former regexp")
            )
            .build()
        val fieldTransform = FieldTransform.newBuilder()
            .setField(attribute)
            .addTransforms(fallbackTransform)
            .build()

        // When
        val field = underTest.toJooqField(attribute, fieldTransform)

        // Then
        field.toSql() shouldBe "'former regexp' \"email\""
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
        val filter = DataPolicy.RuleSet.Filter.newBuilder()
            .setGenericFilter(
                GenericFilter.newBuilder()
                    .addAllConditions(
                        listOf(
                            GenericFilter.Condition.newBuilder()
                                .addAllPrincipals(listOf("fraud_and_risk").toPrincipals())
                                .setCondition("true")
                                .build(),
                            GenericFilter.Condition.newBuilder()
                                .addAllPrincipals(listOf("analytics", "marketing").toPrincipals())
                                .setCondition("age > 18")
                                .build(),
                            GenericFilter.Condition.newBuilder()
                                .setCondition("false")
                                .build()
                        )
                    )
            )
            .build()

        // When
        val condition = underTest.toCondition(filter.genericFilter)

        // Then
        condition.toSql() shouldBe """(1 = (case when (IS_ROLEMEMBER('fraud_and_risk')=1) then (case
  when (1=1) then 1
  else 0
end) when ((IS_ROLEMEMBER('analytics')=1) or (IS_ROLEMEMBER('marketing')=1)) then (case
  when (age > 18) then 1
  else 0
end) else (case
  when (1=0) then 1
  else 0
end) end))""".trimMargin()
    }

    @Test
    fun `single retention to condition`() {
        // Given
        val retention = RetentionFilter.newBuilder()
            .setField(DataPolicy.Field.newBuilder().addNameParts("timestamp"))
            .addAllConditions(
                listOf(
                    RetentionFilter.Condition.newBuilder()
                        .addPrincipals(Principal.newBuilder().setGroup("marketing"))
                        .setPeriod(RetentionFilter.Period.newBuilder().setDays(5))
                        .build(),
                    RetentionFilter.Condition.newBuilder()
                        .addPrincipals(Principal.newBuilder().setGroup("fraud_and_risk"))
                        .build(),
                    RetentionFilter.Condition.newBuilder()
                        .addPrincipals(Principal.getDefaultInstance())
                        .setPeriod(RetentionFilter.Period.newBuilder().setDays(10))
                        .build()
                )
            ).build()

        // When
        val condition = underTest.toCondition(retention)

        // Then
        condition.toSql() shouldBe """
            dateadd(day, (case
              when (IS_ROLEMEMBER('marketing')=1) then 5
              when (IS_ROLEMEMBER('fraud_and_risk')=1) then 100000
              else 10
            end), timestamp) > current_timestamp""".trimIndent()
    }

    @Test
    fun `full sql view statement with multiple retentions`() {
        // Given
        val viewGenerator = SynapseViewGenerator(multipleRetentionPolicy) { withRenderFormatted(true) }
        // When

        // Then
        viewGenerator.toDynamicViewSQL().sql shouldBe """create view public.demo_view
as
select
  ts,
  validThrough,
  userid,
  transactionamount
from public.demo_tokenized
where (
  (1 = (case
    when (IS_ROLEMEMBER('fraud_and_risk')=1) then (case
  when (1=1) then 1
  else 0
end)
    else (case
  when (transactionamount < 10) then 1
  else 0
end)
  end))
  and dateadd(day, (case
  when (IS_ROLEMEMBER('marketing')=1) then 5
  when (IS_ROLEMEMBER('fraud_and_risk')=1) then 100000
  else 10
end), ts) > current_timestamp
  and dateadd(day, (case
  when (IS_ROLEMEMBER('fraud_and_risk')=1) then 365
  else 0
end), validThrough) > current_timestamp
);"""
    }

    @Test
    fun `full sql view statement with single retention`() {
        // Given
        val viewGenerator = SynapseViewGenerator(singleRetentionPolicy) { withRenderFormatted(true) }
        // When

        // Then
        viewGenerator.toDynamicViewSQL().sql shouldBe """create view public.demo_view
as
select
  ts,
  userid,
  transactionamount
from public.demo_tokenized
where (
  (1 = (case
    when (IS_ROLEMEMBER('fraud_and_risk')=1) then (case
  when (1=1) then 1
  else 0
end)
    else (case
  when (transactionamount < 10) then 1
  else 0
end)
  end))
  and dateadd(day, (case
  when (IS_ROLEMEMBER('marketing')=1) then 5
  when (IS_ROLEMEMBER('fraud_and_risk')=1) then 100000
  else 10
end), ts) > current_timestamp
);"""
    }

    @Test
    fun `transform test various transforms`() {
        underTest = SynapseViewGenerator(policyWithoutRegexes) { withRenderFormatted(true) }
        underTest.toDynamicViewSQL().sql
            .shouldBe(
                """create view my_schema.gddemo_public
as
select
  transactionId,
  case
    when (IS_ROLEMEMBER('fraud_and_risk')=1) then userId
    else HASHBYTES('SHA2_512', userId)
  end userId,
  case
    when (
      (IS_ROLEMEMBER('analytics')=1)
      or (IS_ROLEMEMBER('marketing')=1)
    ) then 'former regexp'
    when (
      (IS_ROLEMEMBER('fraud_and_risk')=1)
      or (IS_ROLEMEMBER('admin')=1)
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
from my_schema.gddemo
where (
  (1 = (case
    when (IS_ROLEMEMBER('fraud_and_risk')=1) then (case
  when (1=1) then 1
  else 0
end)
    else (case
  when (age > 18) then 1
  else 0
end)
  end))
  and (1 = (case
    when (IS_ROLEMEMBER('marketing')=1) then (case
  when (userId in ('1', '2', '3', '4')) then 1
  else 0
end)
    else (case
  when (1=1) then 1
  else 0
end)
  end))
  and (1 = ((case
  when (transactionAmount < 10) then 1
  else 0
end)))
);"""
            )
    }

    @Test
    fun `grant select permission`() {
        underTest = SynapseViewGenerator(policyWithoutRegexes) { withRenderFormatted(true) }
        underTest.grantSelectPrivileges().sql
            .shouldBe(
                """grant SELECT on my_schema.gddemo_public to "analytics";
grant SELECT on my_schema.gddemo_public to "marketing";
grant SELECT on my_schema.gddemo_public to "fraud_and_risk";
grant SELECT on my_schema.gddemo_public to "admin";"""
            )
    }

    @Test
    fun `transform - no row filters`() {
        val policyWithoutFilters = dataPolicy.toBuilder().apply { ruleSetsBuilderList.first().clearFilters() }.build()
        underTest = SynapseViewGenerator(policyWithoutFilters) { withRenderFormatted(true) }
        shouldThrow<BadRequestException> { underTest.toDynamicViewSQL() }
    }

    @Test
    fun `transform without regex replace - no row filters`() {
        underTest = SynapseViewGenerator(policyWithoutFiltersAndRegexes) { withRenderFormatted(true) }
        underTest.toDynamicViewSQL().sql
            .shouldBe(
                """create view my_schema.gddemo_public
as
select
  transactionId,
  case
    when (IS_ROLEMEMBER('fraud_and_risk')=1) then userId
    else HASHBYTES('SHA2_512', userId)
  end userId,
  case
    when (
      (IS_ROLEMEMBER('analytics')=1)
      or (IS_ROLEMEMBER('marketing')=1)
    ) then 'former regexp'
    when (
      (IS_ROLEMEMBER('fraud_and_risk')=1)
      or (IS_ROLEMEMBER('admin')=1)
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
from my_schema.gddemo;"""
            )
    }


    @Test
    fun `single generic condition`() {
        val f = GenericFilter.newBuilder()
            .addAllConditions(
                listOf(
                    GenericFilter.Condition.newBuilder()
                        .addAllPrincipals(groupPrincipals("fraud_and_risk"))
                        .setCondition("1=1")
                        .build(),
                    GenericFilter.Condition.newBuilder()
                        .addAllPrincipals(groupPrincipals("marketing", "sales"))
                        .setCondition("transactionamount < 100")
                        .build(),
                    GenericFilter.Condition.newBuilder()
                        .setCondition("transactionamount < 10")
                        .build()
                )
            )
            .build()
        val sql = underTest.toCondition(f)
        println(sql.toSql())
    }

    @Test
    fun `single retention condition`() {
        val f = RetentionFilter.newBuilder()
            .setField(DataPolicy.Field.newBuilder().addNameParts("ts"))
            .addAllConditions(
                listOf(
                    RetentionFilter.Condition.newBuilder()
                        .addAllPrincipals(groupPrincipals("fraud_and_risk"))
                        .build(),
                    RetentionFilter.Condition.newBuilder()
                        .addAllPrincipals(groupPrincipals("marketing", "sales"))
                        .setPeriod(RetentionFilter.Period.newBuilder().setDays(3))
                        .build(),
                    RetentionFilter.Condition.newBuilder()
                        .setPeriod(RetentionFilter.Period.newBuilder().setDays(10))
                        .build(),
                )
            )
            .build()
        val sql = underTest.toCondition(f)
        println(sql.toSql())
    }

    companion object {
        @Language("yaml")
        private val dataPolicy = """
        source: 
          type: SQL_DDL
          spec: |-
            CREATE TABLE mycatalog.my_schema.gddemo (
              transactionId bigint,
              userId string,
              email string,
              age bigint,
              size string,
              brand string,
              transactionAmount bigint,
              items string,
              itemCount bigint,
              date timestamp,
              purpose bigint
            );
          ref: mycatalog.my_schema.gddemo
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
            type: DYNAMIC_VIEW
            fullname: 'my_catalog.my_schema.gddemo_public'
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
                    - group: fraud_and_risk
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
                    - group: fraud_and_risk
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
            - generic_filter:
                conditions:
                  - principals:
                      - group: fraud_and_risk
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
        info:
          title: "Data Policy for Pace Synapse Demo Dataset"
          description: "Pace Demo Dataset"
          version: "1.0.0"
          create_time: "2023-09-26T16:33:51.150Z"
          update_time: "2023-09-26T16:33:51.150Z"
                  """.toProto<DataPolicy>()

        val policyWithoutRegexes: DataPolicy = dataPolicy.toBuilder().apply {
            val fieldTransforms = this.ruleSetsBuilderList.first().fieldTransformsList.map { fieldTransform ->
                FieldTransform.newBuilder()
                    .setField(fieldTransform.field)
                    .addAllTransforms(
                        fieldTransform.transformsList.map {
                            if (it.hasRegexp()) {
                                it.toBuilder().clearRegexp()
                                    .setFixed(FieldTransform.Transform.Fixed.newBuilder().setValue("former regexp"))
                                    .build()
                            } else {
                                it
                            }
                        }
                    ).build()
            }
            ruleSetsBuilderList.first().clearFieldTransforms()
                .addAllFieldTransforms(fieldTransforms)
        }.build()

        val policyWithoutFiltersAndRegexes: DataPolicy = policyWithoutRegexes.toBuilder().apply {
            this.ruleSetsBuilderList.first().clearFilters()
        }.build()


        @Language("yaml")
        val singleRetentionPolicy = """
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
                  ref: public.demo_tokenized
                rule_sets:
                  - target:
                      fullname: public.demo_view
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
                                days: 5
                            - principals: [ {group: fraud_and_risk} ]
                            - principals: [] 
                              period:
                                days: 10
            """.trimIndent().toProto<DataPolicy>()

        @Language("yaml")
        val multipleRetentionPolicy = """
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
                  ref: public.demo_tokenized
                rule_sets:
                  - target:
                      fullname: public.demo_view
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
                                days: 5
                            - principals: [ {group: fraud_and_risk} ]
                            - principals: [] 
                              period:
                                days: 10
                      - retention_filter:
                          field:
                            name_parts:
                              - validThrough
                            required: true
                            type: timestamp
                          conditions:
                            - principals: [ {group: fraud_and_risk} ]
                              period:
                                days: 365
                            - principals: [] 
                              period:
                                days: 0
            """.trimIndent().toProto<DataPolicy>()
    }


    private fun groupPrincipals(vararg p: String): List<Principal> =
        p.map {
            Principal.newBuilder()
                .setGroup(it)
                .build()
        }
}
