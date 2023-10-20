package com.getstrm.daps.databricks

import com.getstrm.daps.domain.ProcessingPlatformExecuteException
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.DATABRICKS
import com.databricks.sdk.AccountClient
import com.databricks.sdk.WorkspaceClient
import com.databricks.sdk.core.DatabricksConfig as DatabricksClientConfig
import com.databricks.sdk.service.catalog.TableInfo
import com.databricks.sdk.service.iam.ListAccountGroupsRequest
import com.databricks.sdk.service.sql.ExecuteStatementRequest
import com.databricks.sdk.service.sql.ExecuteStatementResponse
import com.databricks.sdk.service.sql.StatementState
import com.getstrm.daps.config.DatabricksConfig
import com.getstrm.daps.domain.*
import org.slf4j.LoggerFactory

class DatabricksClient(
    override val id: String,
    val config: DatabricksConfig,
) : ProcessingPlatformInterface {

    constructor(config: DatabricksConfig) : this(config.id, config)

    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    private val workspaceClient = DatabricksClientConfig()
        .setHost(config.workspaceHost)
        .setClientId(config.clientId)
        .setClientSecret(config.clientSecret).let { config ->
            WorkspaceClient(config)
        }

    private val accountClient = DatabricksClientConfig()
        .setHost(config.accountHost)
        .setAccountId(config.accountId)
        .setClientId(config.clientId)
        .setClientSecret(config.clientSecret).let { config ->
            AccountClient(config)
        }

    override val type
        get() = DATABRICKS

    override suspend fun listGroups(): List<Group> =
        accountClient.groups().list(ListAccountGroupsRequest()).map { group ->
            Group(id = group.id, name = group.displayName)
        }

    override suspend fun listTables(): List<Table> = listAllTables().map { DatabricksTable(it.fullName, it) }

    /**
     * Lists all tables (or views) in all schemas in all catalogs that the service principal has access to.
     */
    fun listAllTables(): List<TableInfo> {
        val catalogNames = workspaceClient.catalogs().list().map { it.name }
        val schemas = catalogNames.flatMap { catalog -> workspaceClient.schemas().list(catalog) }
        val tableInfoList = schemas.flatMap { schema -> workspaceClient.tables().list(schema.catalogName, schema.name) }
        return tableInfoList.filter { it.catalogName == config.catalog && it.owner != "System user" }
    }

    /**
     * Lists all tables (or views) in a specific schema in a specific catalog that the service principal has access to.
     */
    fun listSchemaTables(catalogName: String, schemaName: String): List<TableInfo> {
        return workspaceClient.tables().list(catalogName, schemaName).toList()
    }

    fun executeStatement(
        statement: String,
        catalog: String? = null,
        schema: String? = null,
    ): ExecuteStatementResponse {
        requireNotNull(config.warehouseId) { "Warehouse id must be set to execute a statement" }

        return workspaceClient.statementExecution().executeStatement(
            ExecuteStatementRequest()
                .setWarehouseId(config.warehouseId)
                .apply { if (catalog != null) setCatalog(catalog) }
                .apply { if (schema != null) setSchema(schema) }
                .setStatement(statement),
        )
    }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val statement = DatabricksDynamicViewGenerator(dataPolicy).toDynamicViewSQL()
        val response = executeStatement(statement)
        when (response.status.state) {
            StatementState.SUCCEEDED -> return
            else ->
                if (response.status.error.errorCode != null) {
                    log.warn("SQL statement\n{}", statement)
                    val errorMessage = "Databricks response %s: %s".format(response.status.error, response.status.error.message)
                    log.warn("Caused error {}", errorMessage)
                    throw ProcessingPlatformExecuteException(id, "Failed to apply policy: $errorMessage")
                }
        }
    }
}

class DatabricksTable(
    override val fullName: String,
    private val tableInfo: TableInfo,
) : Table() {

    override suspend fun toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy = tableInfo.toDataPolicy(platform)
}
