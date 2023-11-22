package com.getstrm.pace.service

import com.getstrm.pace.toPrincipal
import com.getstrm.pace.util.parseDataPolicy
import com.getstrm.pace.util.yaml2json
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class DataPolicyEvaluationServiceTest {

    private val underTest = DataPolicyEvaluationService()

    @Test
    fun `evaluate a basic policy with various transforms and principals`() {
        // When
        val results = underTest.evaluatePolicy(dataPolicy, csvInput)

        // Then
        val resultsByPrincipal = results.associateBy {
            if (it.hasPrincipal()) {
                it.principal.group
            } else {
                "none"
            }
        }
        resultsByPrincipal["administrator"]!!.csv shouldBe administratorResult
        resultsByPrincipal["administrator"]!!.principal shouldBe "administrator".toPrincipal()
        resultsByPrincipal["fraud_and_risk"]!!.csv shouldBe fraudAndRiskResult
        resultsByPrincipal["fraud_and_risk"]!!.principal shouldBe "fraud_and_risk".toPrincipal()
        resultsByPrincipal["marketing"]!!.csv shouldBe marketingResult
        resultsByPrincipal["marketing"]!!.principal shouldBe "marketing".toPrincipal()
        resultsByPrincipal["none"]!!.csv shouldBe fallbackResult
        resultsByPrincipal["none"]!!.hasPrincipal() shouldBe false
    }

    companion object {
        @Language("yaml")
        private val dataPolicy = """
metadata:
  description: ""
  version: 1
  title: public.demo
platform:
  id: standalone-sample-connection
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
      - generic_filter:
          conditions:
            - principals: [ { group: administrator }, { group: fraud_and_risk } ]
              condition: "true"
            - principals: [ ]
              condition: "age > 8"
    field_transforms:
      - field:
          name_parts: [ userid ]
        transforms:
          - principals: [ { group: fraud_and_risk }, { group: administrator } ]
            identity: { }
          - principals: [ ]
            fixed:
              value: "0000"
      - field:
          name_parts: [ email ]
        transforms:
          - principals: [ { group: administrator } ]
            identity: { }
          - principals: [ { group: marketing } ]
            regexp:
              regexp: "^.*(@.*)${'$'}"
              replacement: "****${'$'}1"
          - principals: [ { group: fraud_and_risk } ]
            identity: { }
          - principals: [ ]
            fixed:
              value: "****"
      - field:
          name_parts: [ brand ]
        transforms:
          - principals: [ { group: administrator } ]
            identity: { }
          - principals: [ ]
            sql_statement:
              statement: "CASE WHEN brand = 'Macbook' THEN 'Apple' ELSE 'Other' END"
    """.yaml2json().parseDataPolicy()

    }

    private val csvInput = """
        transactionid,userid,email,age,transactionamount,brand
        861200791,533445,jeffreypowell@hotmail.com,33,123,Lenovo
        733970993,468355,forbeserik@gmail.com,16,46,Macbook
        494723158,553892,wboone@gmail.com,64,73,Lenovo
        208276802,774142,oliverjulie@yahoo.com,12,16,Lenovo
        699389675,267574,debra64@hotmail.com,79,186,Macbook
        174740434,844701,blewis@yahoo.com,44,232,HP
        970093468,839306,smartin@yahoo.com,32,130,Lenovo
        517552942,257977,tmaynard@hotmail.com,82,259,Lenovo
        537925988,517692,vrice@yahoo.com,23,134,Lenovo
        132876492,460057,robertflowers@hotmail.com,8,186,Macbook
        560312781,423577,danielle87@hotmail.com,94,162,Lenovo
        961847769,573171,tfleming@hotmail.com,21,46,Acer
        423973835,722699,obennett@hotmail.com,66,179,Lenovo
        719567603,403972,goodmangail@hotmail.com,86,29,HP
        298794071,160160,twalker@yahoo.com,69,56,Lenovo
        739934738,657878,heathercollins@yahoo.com,33,226,Macbook
        741524747,213949,omartin@yahoo.com,55,92,Acer
        473108992,779506,kennethreid@yahoo.com,55,196,HP
        601886496,393471,kthompson@gmail.com,42,190,Macbook
        270057253,285843,lyonsluis@hotmail.com,7,12,HP
        458977536,740948,stevencarr@yahoo.com,75,65,Acer
        800416138,883485,allenrobert@gmail.com,15,77,Lenovo
        519500819,192420,rogerselizabeth@hotmail.com,66,152,HP
        629637561,728380,tinawhite@gmail.com,1,180,Acer
        534704584,870941,acole@gmail.com,4,7,HP
        807835672,867943,knappjeremy@hotmail.com,49,10,Acer
        467414030,251481,morriserin@hotmail.com,6,277,Acer
        994186205,500392,wgolden@yahoo.com,68,160,Lenovo
        217127008,143855,nelsondaniel@hotmail.com,28,263,Lenovo
        142409570,567637,meganriley@gmail.com,56,296,Acer
    """.trimIndent()

    private val administratorResult = """
        transactionid,userid,email,age,brand,transactionamount
        861200791,533445,jeffreypowell@hotmail.com,33,Lenovo,123
        733970993,468355,forbeserik@gmail.com,16,Macbook,46
        494723158,553892,wboone@gmail.com,64,Lenovo,73
        208276802,774142,oliverjulie@yahoo.com,12,Lenovo,16
        699389675,267574,debra64@hotmail.com,79,Macbook,186
        174740434,844701,blewis@yahoo.com,44,HP,232
        970093468,839306,smartin@yahoo.com,32,Lenovo,130
        517552942,257977,tmaynard@hotmail.com,82,Lenovo,259
        537925988,517692,vrice@yahoo.com,23,Lenovo,134
        132876492,460057,robertflowers@hotmail.com,8,Macbook,186
        560312781,423577,danielle87@hotmail.com,94,Lenovo,162
        961847769,573171,tfleming@hotmail.com,21,Acer,46
        423973835,722699,obennett@hotmail.com,66,Lenovo,179
        719567603,403972,goodmangail@hotmail.com,86,HP,29
        298794071,160160,twalker@yahoo.com,69,Lenovo,56
        739934738,657878,heathercollins@yahoo.com,33,Macbook,226
        741524747,213949,omartin@yahoo.com,55,Acer,92
        473108992,779506,kennethreid@yahoo.com,55,HP,196
        601886496,393471,kthompson@gmail.com,42,Macbook,190
        270057253,285843,lyonsluis@hotmail.com,7,HP,12
        458977536,740948,stevencarr@yahoo.com,75,Acer,65
        800416138,883485,allenrobert@gmail.com,15,Lenovo,77
        519500819,192420,rogerselizabeth@hotmail.com,66,HP,152
        629637561,728380,tinawhite@gmail.com,1,Acer,180
        534704584,870941,acole@gmail.com,4,HP,7
        807835672,867943,knappjeremy@hotmail.com,49,Acer,10
        467414030,251481,morriserin@hotmail.com,6,Acer,277
        994186205,500392,wgolden@yahoo.com,68,Lenovo,160
        217127008,143855,nelsondaniel@hotmail.com,28,Lenovo,263
        142409570,567637,meganriley@gmail.com,56,Acer,296

    """.trimIndent()

    private val fraudAndRiskResult = """
        transactionid,userid,email,age,brand,transactionamount
        861200791,533445,jeffreypowell@hotmail.com,33,Other,123
        733970993,468355,forbeserik@gmail.com,16,Apple,46
        494723158,553892,wboone@gmail.com,64,Other,73
        208276802,774142,oliverjulie@yahoo.com,12,Other,16
        699389675,267574,debra64@hotmail.com,79,Apple,186
        174740434,844701,blewis@yahoo.com,44,Other,232
        970093468,839306,smartin@yahoo.com,32,Other,130
        517552942,257977,tmaynard@hotmail.com,82,Other,259
        537925988,517692,vrice@yahoo.com,23,Other,134
        132876492,460057,robertflowers@hotmail.com,8,Apple,186
        560312781,423577,danielle87@hotmail.com,94,Other,162
        961847769,573171,tfleming@hotmail.com,21,Other,46
        423973835,722699,obennett@hotmail.com,66,Other,179
        719567603,403972,goodmangail@hotmail.com,86,Other,29
        298794071,160160,twalker@yahoo.com,69,Other,56
        739934738,657878,heathercollins@yahoo.com,33,Apple,226
        741524747,213949,omartin@yahoo.com,55,Other,92
        473108992,779506,kennethreid@yahoo.com,55,Other,196
        601886496,393471,kthompson@gmail.com,42,Apple,190
        270057253,285843,lyonsluis@hotmail.com,7,Other,12
        458977536,740948,stevencarr@yahoo.com,75,Other,65
        800416138,883485,allenrobert@gmail.com,15,Other,77
        519500819,192420,rogerselizabeth@hotmail.com,66,Other,152
        629637561,728380,tinawhite@gmail.com,1,Other,180
        534704584,870941,acole@gmail.com,4,Other,7
        807835672,867943,knappjeremy@hotmail.com,49,Other,10
        467414030,251481,morriserin@hotmail.com,6,Other,277
        994186205,500392,wgolden@yahoo.com,68,Other,160
        217127008,143855,nelsondaniel@hotmail.com,28,Other,263
        142409570,567637,meganriley@gmail.com,56,Other,296

    """.trimIndent()

    private val marketingResult = """
        transactionid,userid,email,age,brand,transactionamount
        861200791,0,****@hotmail.com,33,Other,123
        733970993,0,****@gmail.com,16,Apple,46
        494723158,0,****@gmail.com,64,Other,73
        208276802,0,****@yahoo.com,12,Other,16
        699389675,0,****@hotmail.com,79,Apple,186
        174740434,0,****@yahoo.com,44,Other,232
        970093468,0,****@yahoo.com,32,Other,130
        517552942,0,****@hotmail.com,82,Other,259
        537925988,0,****@yahoo.com,23,Other,134
        560312781,0,****@hotmail.com,94,Other,162
        961847769,0,****@hotmail.com,21,Other,46
        423973835,0,****@hotmail.com,66,Other,179
        719567603,0,****@hotmail.com,86,Other,29
        298794071,0,****@yahoo.com,69,Other,56
        739934738,0,****@yahoo.com,33,Apple,226
        741524747,0,****@yahoo.com,55,Other,92
        473108992,0,****@yahoo.com,55,Other,196
        601886496,0,****@gmail.com,42,Apple,190
        458977536,0,****@yahoo.com,75,Other,65
        800416138,0,****@gmail.com,15,Other,77
        519500819,0,****@hotmail.com,66,Other,152
        807835672,0,****@hotmail.com,49,Other,10
        994186205,0,****@yahoo.com,68,Other,160
        217127008,0,****@hotmail.com,28,Other,263
        142409570,0,****@gmail.com,56,Other,296

    """.trimIndent()

    private val fallbackResult = """
        transactionid,userid,email,age,brand,transactionamount
        861200791,0,****,33,Other,123
        733970993,0,****,16,Apple,46
        494723158,0,****,64,Other,73
        208276802,0,****,12,Other,16
        699389675,0,****,79,Apple,186
        174740434,0,****,44,Other,232
        970093468,0,****,32,Other,130
        517552942,0,****,82,Other,259
        537925988,0,****,23,Other,134
        560312781,0,****,94,Other,162
        961847769,0,****,21,Other,46
        423973835,0,****,66,Other,179
        719567603,0,****,86,Other,29
        298794071,0,****,69,Other,56
        739934738,0,****,33,Apple,226
        741524747,0,****,55,Other,92
        473108992,0,****,55,Other,196
        601886496,0,****,42,Apple,190
        458977536,0,****,75,Other,65
        800416138,0,****,15,Other,77
        519500819,0,****,66,Other,152
        807835672,0,****,49,Other,10
        994186205,0,****,68,Other,160
        217127008,0,****,28,Other,263
        142409570,0,****,56,Other,296

    """.trimIndent()
}
