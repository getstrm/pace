package com.getstrm.pace.util

import extractFieldPaths
import io.kotest.matchers.collections.shouldContainAll
import org.junit.jupiter.api.Test
import parseDataPolicy
import yaml2json

class UtilTest {

    @Test
    fun `extract field paths from spec`() {
        dataPolicy.source.extractFieldPaths()
            .shouldContainAll(dataPolicy.source.attributesList)
    }

    companion object {
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
      hairColor string,
      transactionAmount bigint,
      items string,
      itemCount bigint,
      date timestamp,
      purpose bigint
    );
  ref: mycatalog.my_schema.gddemo
  attributes:
    - path_components: [transactionId]
      type: bigint
    - path_components: [userId]
      type: string
    - path_components: [email]
      type: string
    - path_components: [age]
      type: bigint
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
    fullname: 'my_catalog.my_schema.gddemo_public'
  field_transforms:
    - attribute:
        path_components:
          - email
      transforms:
        - principals:
            - analytics
            - marketing
          regex:
            regex: '^.*(@.*)${'$'}'
            replacement: '****${'$'}1'
        - principals:
            - fraud-detection
            - admin
          identity: true
        - principals: []
          fixed:
            value: "****"
    - attribute:
        path_components:
          - userId
      transforms:
        - principals:
            - fraud-detection
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
  filters:
    - attribute:
        path_components:
          - age
      conditions:
        - principals:
            - fraud-detection
          condition: "true"
        - principals: []
          condition: "age > 18"
    - attribute:
        path_components:
          - userId
      conditions:
        - principals:
            - marketing
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
