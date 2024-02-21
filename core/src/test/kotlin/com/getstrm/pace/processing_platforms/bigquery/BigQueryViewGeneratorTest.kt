package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.Principal
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.GenericFilter
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.RetentionFilter
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import com.getstrm.pace.namedField
import com.getstrm.pace.toPrincipals
import com.getstrm.pace.toSql
import com.getstrm.pace.util.toProto
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BigQueryViewGeneratorTest {

    private lateinit var underTest: BigQueryViewGenerator
    private val defaultUserGroupsTable = "my_project.my_dataset.my_user_groups"
    private val bqContext = DSL.using(SQLDialect.MYSQL)

    @BeforeEach
    fun setUp() {
        val basicPolicy =
            DataPolicy.newBuilder()
                .apply {
                    sourceBuilder.setRef(
                        resourceUrn { integrationFqn = "my-project.my_dataset.my_source_table" }
                    )
                }
                .build()
        underTest = BigQueryViewGenerator(basicPolicy, defaultUserGroupsTable)
    }

    @Test
    fun `principal check with multiple principals`() {
        // Given
        val principals = listOf("ANALYTICS", "MARKETING").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition!!.toSql(bqContext) shouldBe
            "(('ANALYTICS' IN ( SELECT `userGroup` FROM `user_groups` )) or ('MARKETING' IN ( SELECT `userGroup` FROM `user_groups` )))"
    }

    @Test
    fun `principal check with a single principal`() {
        // Given
        val principals = listOf("ANALYTICS").toPrincipals()

        // When
        val condition = underTest.toPrincipalCondition(principals)

        // Then
        condition!!.toSql(bqContext) shouldBe
            "('ANALYTICS' IN ( SELECT `userGroup` FROM `user_groups` ))"
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
                .addAllPrincipals(listOf("FRAUD_DETECTION").toPrincipals())
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
        val jooqField =
            underTest.toJooqField(field, fieldTransform, DataPolicy.Target.getDefaultInstance())

        // Then
        // Fixme: when calling toJooqField directly, there is additional escaping on the field
        // alias, for some reason.
        jooqField.toSql(bqContext) shouldBe
            "case when (('ANALYTICS' IN ( SELECT `userGroup` FROM `user_groups` )) or ('MARKETING' IN ( SELECT `userGroup` FROM `user_groups` ))) then '****' when ('FRAUD_DETECTION' IN ( SELECT `userGroup` FROM `user_groups` )) then 'REDACTED EMAIL' else 'fixed-value' end as ```email```"
    }

    @Test
    fun `row filters to condition`() {
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
        val condition =
            underTest.toCondition(filter.genericFilter, DataPolicy.Target.getDefaultInstance())

        // Then
        condition.toSql(bqContext) shouldBe
            "case when ('fraud-and-risk' IN ( SELECT `userGroup` FROM `user_groups` )) then true when (('analytics' IN ( SELECT `userGroup` FROM `user_groups` )) or ('marketing' IN ( SELECT `userGroup` FROM `user_groups` ))) then age > 18 else false end"
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
                            .addPrincipals(Principal.newBuilder().setGroup("marketing"))
                            .setPeriod(RetentionFilter.Period.newBuilder().setDays(5))
                            .build(),
                        RetentionFilter.Condition.newBuilder()
                            .addPrincipals(Principal.newBuilder().setGroup("fraud-and-risk"))
                            .build(),
                        RetentionFilter.Condition.newBuilder()
                            .addPrincipals(Principal.getDefaultInstance())
                            .setPeriod(RetentionFilter.Period.newBuilder().setDays(10))
                            .build()
                    )
                )
                .build()

        // When
        val condition = underTest.toCondition(retention, DataPolicy.Target.getDefaultInstance())

        // Then
        condition.toSql(bqContext) shouldBe
            """
            case when ('marketing' IN ( SELECT `userGroup` FROM `user_groups` )) then TIMESTAMP_ADD(timestamp, INTERVAL 5 DAY) > current_timestamp() when ('fraud-and-risk' IN ( SELECT `userGroup` FROM `user_groups` )) then true else TIMESTAMP_ADD(timestamp, INTERVAL 10 DAY) > current_timestamp() end
        """
                .trimIndent()
    }

    @Test
    fun `full SQL view statement with a single detokenize join`() {
        // Given
        val viewGenerator =
            BigQueryViewGenerator(singleDetokenizePolicy, defaultUserGroupsTable) {
                withRenderFormatted(true)
            }
        viewGenerator.toDynamicViewSQL().sql shouldBe
            """create or replace view `my-project.my_dataset.my_target_view`
as
with
  user_groups as (
    select userGroup
    from `my_project.my_dataset.my_user_groups`
    where userEmail = SESSION_USER()
  )
select
  `transactionId`,
  case
    when ('fraud_and_risk' IN ( SELECT `userGroup` FROM `user_groups` )) then coalesce(`my-project.tokens.userid_tokens`.`userId`, `my-project.my_dataset.my_source_table`.`userId`)
    else `userId`
  end `userId`,
  `transactionAmount`
from `my-project.my_dataset.my_source_table`
  left outer join `my-project.tokens.userid_tokens`
    on (`my-project.my_dataset.my_source_table`.userId = `my-project.tokens.userid_tokens`.token)
where case
  when ('fraud_and_risk' IN ( SELECT `userGroup` FROM `user_groups` )) then true
  else transactionAmount < 10
end;"""
    }

    @Test
    fun `full SQL view statement with multiple detokenize joins on two tables`() {
        // Given
        val viewGenerator =
            BigQueryViewGenerator(multiDetokenizePolicy, defaultUserGroupsTable) {
                withRenderFormatted(true)
            }
        viewGenerator.toDynamicViewSQL().sql shouldBe
            """create or replace view `my-project.my_dataset.my_target_view`
as
with
  user_groups as (
    select userGroup
    from `my_project.my_dataset.my_user_groups`
    where userEmail = SESSION_USER()
  )
select
  case
    when ('fraud_and_risk' IN ( SELECT `userGroup` FROM `user_groups` )) then coalesce(`my-project.tokens.transactionid_tokens`.`transactionId`, `my-project.my_dataset.my_source_table`.`transactionId`)
    else `transactionId`
  end `transactionId`,
  case
    when ('fraud_and_risk' IN ( SELECT `userGroup` FROM `user_groups` )) then coalesce(`my-project.tokens.userid_tokens`.`userId`, `my-project.my_dataset.my_source_table`.`userId`)
    else `userId`
  end `userId`,
  `transactionAmount`
from `my-project.my_dataset.my_source_table`
  left outer join `my-project.tokens.userid_tokens`
    on (`my-project.my_dataset.my_source_table`.userId = `my-project.tokens.userid_tokens`.token)
  left outer join `my-project.tokens.transactionid_tokens`
    on (`my-project.my_dataset.my_source_table`.transactionId = `my-project.tokens.transactionid_tokens`.token)
where case
  when ('fraud_and_risk' IN ( SELECT `userGroup` FROM `user_groups` )) then true
  else transactionAmount < 10
end;"""
    }

    @Test
    fun `transform test multiple transforms`() {
        // Given
        underTest =
            BigQueryViewGenerator(dataPolicy, defaultUserGroupsTable) { withRenderFormatted(true) }
        underTest
            .toDynamicViewSQL()
            .sql
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
  case
    when ('FINANCE' IN ( SELECT `userGroup` FROM `user_groups` )) then FARM_FINGERPRINT(CAST(`transactionId` AS STRING))
    else `transactionId`
  end `transactionId`,
  case
    when ('FRAUD_DETECTION' IN ( SELECT `userGroup` FROM `user_groups` )) then CAST(userId AS string)
    else TO_HEX(SHA256(CAST(`userId` AS STRING)))
  end `userId`,
  case
    when (
      ('ANALYTICS' IN ( SELECT `userGroup` FROM `user_groups` ))
      or ('MARKETING' IN ( SELECT `userGroup` FROM `user_groups` ))
    ) then regexp_replace(email, '^.*(@.*)${'$'}', '****\\1')
    when (
      ('FRAUD_DETECTION' IN ( SELECT `userGroup` FROM `user_groups` ))
      or ('ADMIN' IN ( SELECT `userGroup` FROM `user_groups` ))
    ) then `email`
    else '****'
  end `email`,
  `age`,
  `size`,
  case when brand = 'MacBook' then 'Apple' else 'Other' end `brand`,
  `transactionAmount`,
  null `items`,
  `itemCount`,
  `date`,
  `purpose`
from `my_project.my_dataset.my_table`
where (
  case
    when ('FRAUD_DETECTION' IN ( SELECT `userGroup` FROM `user_groups` )) then true
    else age > 18
  end
  and case
    when ('MARKETING' IN ( SELECT `userGroup` FROM `user_groups` )) then userId in ('1', '2', '3', '4')
    else true
  end
  and transactionAmount < 10
);"""
            )
    }

    @Test
    fun `transform test multiple transforms with iam check extensions`() {
        // Given
        underTest =
            BigQueryViewGenerator(dataPolicy, defaultUserGroupsTable, true) {
                withRenderFormatted(true)
            }
        underTest
            .toDynamicViewSQL()
            .sql
            .shouldBe(
                """create or replace view `my_target_project.my_target_dataset.my_target_view`
as
select
  case
    when ("True" in (select principal_check_routines.check_principal_access("group:FINANCE", "my_target_dataset", "my_target_view"))) then FARM_FINGERPRINT(CAST(`transactionId` AS STRING))
    else `transactionId`
  end `transactionId`,
  case
    when ("True" in (select principal_check_routines.check_principal_access("group:FRAUD_DETECTION", "my_target_dataset", "my_target_view"))) then CAST(userId AS string)
    else TO_HEX(SHA256(CAST(`userId` AS STRING)))
  end `userId`,
  case
    when (
      ("True" in (select principal_check_routines.check_principal_access("group:ANALYTICS", "my_target_dataset", "my_target_view")))
      or ("True" in (select principal_check_routines.check_principal_access("group:MARKETING", "my_target_dataset", "my_target_view")))
    ) then regexp_replace(email, '^.*(@.*)$', '****\\1')
    when (
      ("True" in (select principal_check_routines.check_principal_access("group:FRAUD_DETECTION", "my_target_dataset", "my_target_view")))
      or ("True" in (select principal_check_routines.check_principal_access("group:ADMIN", "my_target_dataset", "my_target_view")))
    ) then `email`
    else '****'
  end `email`,
  `age`,
  `size`,
  case when brand = 'MacBook' then 'Apple' else 'Other' end `brand`,
  `transactionAmount`,
  null `items`,
  `itemCount`,
  `date`,
  `purpose`
from `my_project.my_dataset.my_table`
where (
  case
    when ("True" in (select principal_check_routines.check_principal_access("group:FRAUD_DETECTION", "my_target_dataset", "my_target_view"))) then true
    else age > 18
  end
  and case
    when ("True" in (select principal_check_routines.check_principal_access("group:MARKETING", "my_target_dataset", "my_target_view"))) then userId in ('1', '2', '3', '4')
    else true
  end
  and transactionAmount < 10
);"""
            )
    }

    @Test
    fun `roles, permissions and groups bigquery test`() {
        // Given
        underTest =
            BigQueryViewGenerator(dataPolicyBigQueryIAMCheck, null, useIamCheckExtension = true) {
                withRenderFormatted(true)
            }
        underTest
            .toDynamicViewSQL()
            .sql
            .shouldBe(
                """create or replace view `my_target_project.my_target_dataset.my_target_view`
as
select
  `transactionId`,
  case
    when ("True" in (select principal_check_routines.check_principal_access("group:fraud_detection", "my_target_dataset", "my_target_view"))) then CAST(userId AS string)
    else TO_HEX(SHA256(CAST(userId AS string)))
  end `userId`,
  case
    when (
      ("True" in (select principal_check_routines.check_principal_access("group:analytics", "my_target_dataset", "my_target_view")))
      or ("True" in (select principal_check_routines.check_principal_access("permission:bigquery.tables.getData", "my_target_dataset", "my_target_view")))
    ) then regexp_replace(email, '^.*(@.*)$', '****\\1')
    when (
      ("True" in (select principal_check_routines.check_principal_access("group:fraud_detection", "my_target_dataset", "my_target_view")))
      or ("True" in (select principal_check_routines.check_principal_access("role:bigquery.admin", "my_target_dataset", "my_target_view")))
    ) then `email`
    else '****'
  end `email`,
  `age`,
  `size`,
  case when brand = 'MacBook' then 'Apple' else 'Other' end `brand`,
  `transactionAmount`,
  null `items`,
  `itemCount`,
  `date`,
  `purpose`
from `my_project.my_dataset.my_table`
where (
  case
    when ("True" in (select principal_check_routines.check_principal_access("group:fraud_detection", "my_target_dataset", "my_target_view"))) then true
    else age > 18
  end
  and case
    when ("True" in (select principal_check_routines.check_principal_access("permission:bigquery.tables.getData", "my_target_dataset", "my_target_view"))) then userId in ('1', '2', '3', '4')
    else true
  end
  and transactionAmount < 10
);"""
            )
    }

    @Test
    fun `BQ resource name extraction - no integration fqn`() {
        // Given
        val principals = listOf("fraud_detection").toPrincipals()
        val target =
            DataPolicy.Target.newBuilder()
                .setRef(
                    resourceUrn {
                        resourcePath.addAll(
                            listOf(
                                resourceNode { name = "my_target_project" },
                                resourceNode { name = "my_target_dataset" },
                                resourceNode { name = "my_target_view" },
                            )
                        )
                    }
                )
                .build()
        underTest =
            BigQueryViewGenerator(DataPolicy.getDefaultInstance(), null, true) {
                withRenderFormatted(true)
            }
        underTest
            .toPrincipalCondition(principals, target)!!
            .toSql()
            .shouldBe(
                """("True" in (select principal_check_routines.check_principal_access("group:fraud_detection", "my_target_dataset", "my_target_view")))"""
                    .trimIndent()
            )
    }

    companion object {
        @Language("yaml")
        val dataPolicy =
            """
source:
  ref:
    integration_fqn: "my_project.my_dataset.my_table"
    platform: 
      id: bigquery
      platform_type: BIGQUERY
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
        integration_fqn: my_target_project.my_target_dataset.my_target_view
      type: SQL_VIEW
    field_transforms:
      - field:
          name_parts: 
            - transactionId
          type: "bigint"
        transforms:
          - principals:
              - group: FINANCE
            hash: {}
          - principals: []
            identity: {}
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
              replacement: '****$1'
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
            hash: {}
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
                - group: FRAUD_DETECTION
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
        val dataPolicyBigQueryIAMCheck =
            """
source:
  ref:
    integration_fqn: "my_project.my_dataset.my_table"
    platform: 
      id: bigquery
      platform_type: BIGQUERY
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
        integration_fqn: my_target_project.my_target_dataset.my_target_view
      type: SQL_VIEW
    field_transforms:
      - field:
          name_parts:
            - email
          type: "string"
        transforms:
          - principals:
              - group: analytics
              - permission: bigquery.tables.getData
            regexp:
              regexp: '^.*(@.*)${'$'}'
              replacement: '****$1'
          - principals:
              - group: fraud_detection
              - role: bigquery.admin
            identity: {}
          - principals: []
            fixed:
              value: "****"
      - field:
          name_parts:
            - userId
        transforms:
          - principals:
              - group: fraud_detection
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
              statement: "case when brand = 'MacBook' then 'Apple' else 'Other' end"
    filters:
      - generic_filter:
          conditions:
            - principals:
                - group: fraud_detection
              condition: "true"
            - principals: []
              condition: "age > 18"
      - generic_filter:
          conditions:
            - principals:
                - permission: bigquery.tables.getData
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
        val singleDetokenizePolicy =
            """
metadata:
  description: ""
  version: 1
  title: public.demo
source:
  fields:
    - name_parts:
        - transactionId
      required: true
      type: integer
    - name_parts:
        - userId
      required: true
      type: integer
    - name_parts:
        - transactionAmount
      required: true
      type: integer
  ref: 
    integration_fqn: my-project.my_dataset.my_source_table
    platform:
      id: platform-id
      platform_type: POSTGRES
rule_sets:
  - target:
      ref:
        integration_fqn: my-project.my_dataset.my_target_view
    filters:
      - generic_filter:
          conditions:
            - principals: [ {group: fraud_and_risk} ]
              condition: "true"
            - principals : []
              condition: "transactionAmount < 10"
    field_transforms:
      - field:
          name_parts: [ userId ]
        transforms:
          - principals: [ {group: fraud_and_risk} ]
            detokenize:
              token_source_ref: my-project.tokens.userid_tokens
              token_field:
                name_parts: [ token ]
              value_field:
                name_parts: [ userId ]
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
        - transactionId
      required: true
      type: integer
    - name_parts:
        - userId
      required: true
      type: integer
    - name_parts:
        - transactionAmount
      required: true
      type: integer
  ref:
    integration_fqn: my-project.my_dataset.my_source_table
    platform:
      id: platform-id
      platform_type: POSTGRES
rule_sets:
  - target:
      ref:
        integration_fqn: my-project.my_dataset.my_target_view
    filters:
      - generic_filter:
          conditions:
            - principals: [ {group: fraud_and_risk} ]
              condition: "true"
            - principals : []
              condition: "transactionAmount < 10"
    field_transforms:
      - field:
          name_parts: [ userId ]
        transforms:
          - principals: [ {group: fraud_and_risk} ]
            detokenize:
              token_source_ref: my-project.tokens.userid_tokens
              token_field:
                name_parts: [ token ]
              value_field:
                name_parts: [ userId ]
          - principals: []
            identity: {}
      - field:
          name_parts: [ transactionId ]
        transforms:
          - principals: [ {group: fraud_and_risk} ]
            detokenize:
              token_source_ref: my-project.tokens.transactionid_tokens
              token_field:
                name_parts: [ token ]
              value_field:
                name_parts: [ transactionId ]
          - principals: []
            identity: {}
    

            """
                .trimIndent()
                .toProto<DataPolicy>()
    }
}
