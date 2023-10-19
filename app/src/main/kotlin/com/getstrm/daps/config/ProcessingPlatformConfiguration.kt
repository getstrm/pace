package com.getstrm.daps.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.processing-platforms")
data class ProcessingPlatformConfiguration(
    val databricks: List<DatabricksConfig> = emptyList(),
    val snowflake: List<SnowflakeConfig> = emptyList(),
    val bigquery: List<BigQueryConfig> = emptyList(),
)

data class SnowflakeConfig(
    val id: String,
    val serverUrl: String,
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val database: String,
    val warehouse: String,
    val userName: String,
    val password: String,
)

data class BigQueryConfig(
    val id: String,
    val projectId: String,
    val userGroupsTable: String,
    val serviceAccountKeyJson: String,
)

data class DatabricksConfig(
    val id: String,
    val workspaceHost: String,
    val accountHost: String,
    val accountId: String,
    val clientId: String,
    val clientSecret: String,
    val warehouseId: String,
    val catalog: String,
)
