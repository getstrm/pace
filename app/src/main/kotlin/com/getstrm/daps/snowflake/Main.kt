package com.getstrm.daps.snowflake

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.getstrm.jooq.generated.tables.records.ProcessingPlatformTokensRecord
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity

suspend fun main() {
    listTables()
//    val snowflakeClient = SnowflakeClient()
//    val snowflakeTable = snowflakeClient.describeTable()
//    val dataPolicy = snowflakeTable.toDataPolicy()
//    println(dataPolicy)
//    println(snowflakeClient.listGroups())
}



fun listTables() {
    val restTemplate = RestTemplate()
    val objectMapper: ObjectMapper = jacksonObjectMapper()
    val tokens = ProcessingPlatformTokensRecord(
        accessToken = "ver:1-hint:9788067849-ETMsDgAAAYtH2M+nABRBRVMvQ0JDL1BLQ1M1UGFkZGluZwEAABAAEGzCdyLP0KUSNm0ynsslN3QAAABQC7Gi2NfuSAYIf2dSHrhiO+gKFEjYeotLTNXANCDTjEC5eU0ZxRfyVbahGCY7HzpqcvGxP/Qy6UUVGr6d70Xbx+DDMa+hyspMG77r3D8tDo8AFAG9a2mpxrNzbjFWGjxs01f3Iohb"
    )
    val headers = HttpHeaders();
    headers.setBearerAuth(tokens.accessToken.orEmpty())
    headers.contentType = MediaType.APPLICATION_JSON
    headers.accept = listOf(MediaType.APPLICATION_JSON)

    val request = HttpEntity(
        """{
                    "statement": "select table_schema, table_name, table_type, created as create_date, last_altered as modify_date from information_schema.tables where table_type = 'BASE TABLE';",
                    "timeout": 60,
                    "resultSetMetaData": { "format": "json" },
                    "database": "STRM",
                    "warehouse": "COMPUTE_WH"
                }
                    """.trimIndent(),
        headers
    )
    val response = restTemplate.postForEntity<SnowflakeResponse>("https://an49711.eu-central-1.snowflakecomputing.com/api/v2/statements", request)
    response.body?.data.orEmpty().map { (schemaName, tableName) ->
        println("$schemaName.$tableName")
    }
}