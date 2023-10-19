package com.getstrm.daps.snowflake

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SnowflakeResponse (
    val resultSetMetaData: ResultSetMetaData?,
    val code: String,
    val data: List<List<String>>?,
    val createdOn: Long?,
    val message: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ResultSetMetaData (
    val numRows: Int,
    @JsonProperty("rowType")
    val rows: List<RowType>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RowType (
    val name: String,
    val database: String,
    val schema: String,
    val table: String,
    val type: String,
    val nullable: Boolean
)

data class ColumnNameAndType (
    val name: String,
    val type: String,
    val nullable: Boolean
)
