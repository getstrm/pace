package com.getstrm.pace.dbt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.node.ObjectNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class DbtModel(
    val database: String,
    val schema: String,
    val name: String,
    val description: String,
    val columns: Map<String, Column>,
    val tags: List<String>,
    val meta: ObjectNode,
    val fqn: List<String>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Column(
    val name: String,
    val description: String?,
    val tags: List<String>,
    val dataType: String?,
    val meta: ObjectNode,
)
