package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.processing_platforms.postgres.PostgresTransformer
import com.getstrm.pace.processing_platforms.postgres.PostgresViewGenerator
import com.getstrm.pace.util.toProto
import org.intellij.lang.annotations.Language

class DbtApplication {
}
fun main(args: Array<String>) {
    val viewGenerator = PostgresViewGenerator(
        multiDetokenizePolicy,
    )
    val queries = viewGenerator.toDynamicViewSQL()

    println("hallo ${ queries.sql }")
}


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
              condition: "transactionamount < 10"
    field_transforms:
      - field:
          name_parts: [ userid ]
        transforms:
          - principals: [ {group: fraud_and_risk} ]
            detokenize:
              token_source_ref: my-project.tokens.userid_tokens
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
              token_source_ref: my-project.tokens.transactionid_tokens
              token_field:
                name_parts: [ token ]
              value_field:
                name_parts: [ transactionid ]
          - principals: []
            identity: {}
    

            """
        .trimIndent()
        .toProto<DataPolicy>()
