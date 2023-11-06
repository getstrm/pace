package com.getstrm.pace.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.DATABRICKS
import com.databricks.sdk.AccountClient
import com.databricks.sdk.WorkspaceClient
import com.databricks.sdk.core.DatabricksConfig as DatabricksClientConfig
import com.databricks.sdk.service.catalog.TableInfo
import com.databricks.sdk.service.iam.ListAccountGroupsRequest
import com.databricks.sdk.service.sql.ExecuteStatementRequest
import com.databricks.sdk.service.sql.ExecuteStatementResponse
import com.databricks.sdk.service.sql.StatementState
import com.getstrm.pace.config.DatabricksConfig
import com.getstrm.pace.domain.*
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.toTimestamp
import com.google.rpc.DebugInfo
import org.slf4j.LoggerFactory

class DatabricksClient(
    override val id: String,
    val config: DatabricksConfig,
) : ProcessingPlatform {

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

    override val type = DATABRICKS

    override suspend fun listGroups(): List<Group> =
        accountClient.groups().list(ListAccountGroupsRequest()).map { group ->
            Group(id = group.id, name = group.displayName)
        }

    override suspend fun listTables(): List<Table> = listAllTables().map { DatabricksTable(it.fullName, it) }

    /**
     * Lists all tables (or views) in all schemas in all catalogs that the service principal has access to.
     */
    private fun listAllTables(): List<TableInfo> {
        val catalogNames = workspaceClient.catalogs().list().map { it.name }
        val schemas = catalogNames.flatMap { catalog -> workspaceClient.schemas().list(catalog) }
        val tableInfoList = schemas.flatMap { schema -> workspaceClient.tables().list(schema.catalogName, schema.name) }
        return tableInfoList.filter { it.owner != "System user" }
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
                    val errorMessage =
                        "Databricks response %s: %s".format(response.status.error, response.status.error.message)
                    log.warn("Caused error {}", errorMessage)

                    throw InternalException(
                        InternalException.Code.INTERNAL,
                        DebugInfo.newBuilder()
                            .setDetail(
                                "Error while executing Databricks query (error code: ${response.status.error.errorCode.name}), please check the logs of your PACE deployment. $BUG_REPORT"
                            )
                            .addAllStackEntries(listOf(errorMessage))
                            .build()
                    )
                }
        }
    }
}

class DatabricksTable(
    override val fullName: String,
    private val tableInfo: TableInfo,
) : Table() {

    override suspend fun toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy =
        tableInfo.toDataPolicy(platform)

    private fun TableInfo.toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy =
        DataPolicy.newBuilder()
            .setMetadata(
                DataPolicy.Metadata.newBuilder()
                    .setTitle(name)
                    .setDescription(comment.orEmpty())
                    .setCreateTime(createdAt.toTimestamp())
                    .setUpdateTime(updatedAt.toTimestamp()),
            )
            .setPlatform(platform)
            .setSource(
                DataPolicy.Source.newBuilder()
                    .setRef(fullName)
                    .addAllFields(
                        columns.map { column ->
                            DataPolicy.Field.newBuilder()
                                .addAllNameParts(listOf(column.name))
                                .setType(column.typeText)
                                .setRequired(!(column.nullable ?: false))
                                .build().normalizeType()
                        },
                    )
                    .build(),
            )
            .build()

}
