package com.getstrm.pace.processing_platforms.snowflake

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SnowflakeResponse(
    val resultSetMetaData: ResultSetMetaData?,
    val code: String,
    // Todo: make this more type-safe (e.g. create different response types)
    val data: List<List<String>>?,
    val createdOn: Long?,
    val message: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ResultSetMetaData(
    val numRows: Int,
    @JsonProperty("rowType")
    val rows: List<RowType>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RowType(
    val name: String,
    val database: String,
    val schema: String,
    val table: String,
    val type: String,
    val nullable: Boolean
)
