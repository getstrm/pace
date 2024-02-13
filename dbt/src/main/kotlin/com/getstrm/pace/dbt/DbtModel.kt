package com.getstrm.pace.dbt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class DbtModel(
    val database: String,
    val schema: String,
    val name: String,
    val description: String? = null,
    val columns: Map<String, Column> = emptyMap(),
    val tags: List<String> = emptyList(),
    val meta: Map<String, JsonNode> = emptyMap(),
    val fqn: List<String> = emptyList(),
    val originalFilePath: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Column(
    val name: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val dataType: String? = null,
    val meta: Map<String, JsonNode> = emptyMap(),
)
