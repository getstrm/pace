package com.getstrm.daps.config

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy

data class GrpcConfig(
    val port: Int,
    val metricsPort: Int,
)
data class ProcessingPlatformConfig(
    val type: DataPolicy.ProcessingPlatform.PlatformType,
    val id: String,
    val dataBricksConfig: DatabricksConfig?,
    val snowflakeConfig: SnowflakeConfig?,
    val bigqueryConfig: BigQueryConfig?,
) {
    data class SnowflakeConfig(
        val serverUrl: String,
        val token: String?,
        val clientId: String?,
        val clientSecret: String?,
        val redirectUri: String?,
        val database: String,
        val warehouse: String,
    )

    data class BigQueryConfig(
        val projectId: String,
        val userGroupsTable: String,
        val serviceAccountKeyJson: String,
    )

    data class DatabricksConfig(
        val workspaceHost: String,
        val accountHost: String,
        val accountId: String,
        val clientId: String,
        val clientSecret: String,
        val warehouseId: String,
        val catalog: String,
    )
}

data class CatalogConfig(
    val type: CatalogType,
    val id: String,
    val serverUrl: String,
    val token: String?,
    val userName: String?,
    val password: String?,
){
    enum class CatalogType {
        DATAHUB,
        COLLIBRA,
        OPENDATA_DISCOVERY,
    }
}

data class OmaConfig(
    val port: Int,
    val host: String,
    val usePlainText: Boolean
)

data class DatabaseConfig(
    val maximumPoolSize: Int,
    val user: User,
    val connection: Connection,
) {
    internal fun getJdbcUrl() = "jdbc:postgresql://${connection.host}:${connection.port}/${connection.databaseName}"

    data class User(
        val name: String,
        val password: String,
    )

    data class Connection(
        val host: String,
        val port: Int,
        val databaseName: String,
    )
}
