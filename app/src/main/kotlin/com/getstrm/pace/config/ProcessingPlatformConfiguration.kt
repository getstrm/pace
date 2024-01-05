package com.getstrm.pace.config

import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.processing-platforms")
data class ProcessingPlatformConfiguration(
    val databricks: List<DatabricksConfig> = emptyList(),
    val snowflake: List<SnowflakeConfig> = emptyList(),
    val bigquery: List<BigQueryConfig> = emptyList(),
    val postgres: List<PostgresConfig> = emptyList(),
    val synapse: List<SynapseConfig> = emptyList(),
)

open class PPConfig(open val id: String, val type: ProcessingPlatform.PlatformType)

data class SnowflakeConfig(
    override val id: String,
    val serverUrl: String,
    val database: String,
    val warehouse: String,
    val userName: String,
    val accountName: String,
    val organizationName: String,
    val privateKey: String,
) : PPConfig(id, ProcessingPlatform.PlatformType.SNOWFLAKE)

data class BigQueryConfig(
    override val id: String,
    val projectId: String,
    val userGroupsTable: String,
    val serviceAccountJsonKey: String,
) : PPConfig(id, ProcessingPlatform.PlatformType.BIGQUERY)

data class PostgresConfig(
    override val id: String,
    val hostName: String,
    val port: Int,
    val database: String,
    val userName: String,
    val password: String,
) : PPConfig(id, ProcessingPlatform.PlatformType.POSTGRES) {
    internal fun getJdbcUrl() = "jdbc:postgresql://$hostName:$port/$database"
}

data class DatabricksConfig(
    override val id: String,
    val workspaceHost: String,
    val accountHost: String,
    val accountId: String,
    val clientId: String,
    val clientSecret: String,
    val warehouseId: String,
) : PPConfig(id, ProcessingPlatform.PlatformType.DATABRICKS)

data class SynapseConfig(
    override val id: String,
    val hostName: String,
    val port: Int = 1433,
    val database: String,
    val userName: String,
    val password: String,
) : PPConfig(id, ProcessingPlatform.PlatformType.SYNAPSE) {
    internal fun getJdbcUrl() =
        "jdbc:sqlserver://$hostName:$port;database=$database;user=$userName;password=$password;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.sql.azuresynapse.net;loginTimeout=30;"
}
