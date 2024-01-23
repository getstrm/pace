package com.getstrm.pace.config

import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform

data class ProcessingPlatformsConfiguration(
    val databricks: List<DatabricksConfiguration> = emptyList(),
    val snowflake: List<SnowflakeConfiguration> = emptyList(),
    val bigquery: List<BigQueryConfiguration> = emptyList(),
    val postgres: List<PostgresConfiguration> = emptyList(),
    val synapse: List<SynapseConfiguration> = emptyList(),
)

open class ProcessingPlatformConfiguration(
    open val id: String,
    val type: ProcessingPlatform.PlatformType
)

data class SnowflakeConfiguration(
    override val id: String,
    val serverUrl: String,
    val database: String,
    val warehouse: String,
    val userName: String,
    val accountName: String,
    val organizationName: String,
    val privateKey: String,
) : ProcessingPlatformConfiguration(id, ProcessingPlatform.PlatformType.SNOWFLAKE)

data class BigQueryConfiguration(
    override val id: String,
    val projectId: String,
    val userGroupsTable: String,
    val serviceAccountJsonKey: String,
    val useIamCheckExtension: Boolean = false,
) : ProcessingPlatformConfiguration(id, ProcessingPlatform.PlatformType.BIGQUERY)

data class PostgresConfiguration(
    override val id: String,
    val hostName: String,
    val port: Int,
    val database: String,
    val userName: String,
    val password: String,
) : ProcessingPlatformConfiguration(id, ProcessingPlatform.PlatformType.POSTGRES) {
    internal fun getJdbcUrl() = "jdbc:postgresql://$hostName:$port/$database"
}

data class DatabricksConfiguration(
    override val id: String,
    val workspaceHost: String,
    val accountHost: String,
    val accountId: String,
    val clientId: String,
    val clientSecret: String,
    val warehouseId: String,
) : ProcessingPlatformConfiguration(id, ProcessingPlatform.PlatformType.DATABRICKS)

data class SynapseConfiguration(
    override val id: String,
    val hostName: String,
    val port: Int = 1433,
    val database: String,
    val userName: String,
    val password: String,
) : ProcessingPlatformConfiguration(id, ProcessingPlatform.PlatformType.SYNAPSE) {
    internal fun getJdbcUrl() =
        "jdbc:sqlserver://$hostName:$port;database=$database;user=$userName;password=$password;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.sql.azuresynapse.net;loginTimeout=30;"
}
