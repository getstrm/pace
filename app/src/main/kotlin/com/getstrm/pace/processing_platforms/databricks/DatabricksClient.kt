package com.getstrm.pace.processing_platforms.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.DATABRICKS
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.databricks.sdk.AccountClient
import com.databricks.sdk.WorkspaceClient
import com.databricks.sdk.core.DatabricksConfig as DatabricksClientConfig
import com.databricks.sdk.service.catalog.TableInfo
import com.databricks.sdk.service.iam.ListAccountGroupsRequest
import com.databricks.sdk.service.sql.ExecuteStatementRequest
import com.databricks.sdk.service.sql.ExecuteStatementResponse
import com.databricks.sdk.service.sql.StatementState
import com.getstrm.pace.config.DatabricksConfig
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.processing_platforms.Table
import com.getstrm.pace.util.*
import com.google.rpc.DebugInfo
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(DatabricksClient::javaClass.name)

class DatabricksClient(
    override val id: String,
    val config: DatabricksConfig,
) : ProcessingPlatformClient {

    constructor(config: DatabricksConfig) : this(config.id, config)

    private val workspaceClient =
        DatabricksClientConfig()
            .setHost(config.workspaceHost)
            .setClientId(config.clientId)
            .setClientSecret(config.clientSecret)
            .let { config -> WorkspaceClient(config) }

    private val accountClient =
        DatabricksClientConfig()
            .setHost(config.accountHost)
            .setAccountId(config.accountId)
            .setClientId(config.clientId)
            .setClientSecret(config.clientSecret)
            .let { config -> AccountClient(config) }

    override val type = DATABRICKS

    override suspend fun listGroups(pageParameters: PageParameters): PagedCollection<Group> =
        accountClient
            .groups()
            .list(
                ListAccountGroupsRequest().apply {
                    startIndex = pageParameters.skip.toLong()
                    count = pageParameters.pageSize.toLong()
                }
            )
            .map { group -> Group(id = group.id, name = group.displayName) }
            .withPageInfo()

    override suspend fun listTables(pageParameters: PageParameters): PagedCollection<Table> =
        listAllTables(pageParameters).map { DatabricksTable(it.fullName, it, this) }

    /** Lists all tables (or views) in all schemas that the service principal has access to. */
    private fun listAllTables(pageParameters: PageParameters): PagedCollection<TableInfo> {
        // TODO
        // https://linear.app/strmprivacy/issue/PACE-84/processing-platforms-should-have-table-hierarchy-similar-to-catalogs
        val catalogNames = workspaceClient.catalogs().list().map { it.name }
        val schemas = catalogNames.flatMap { catalog -> workspaceClient.schemas().list(catalog) }
        val tableInfoList =
            schemas.flatMap { schema ->
                workspaceClient.tables().list(schema.catalogName, schema.name)
            }
        return tableInfoList
            .filter { it.owner != "System user" }
            .applyPageParameters(pageParameters)
            .withPageInfo()
    }

    /**
     * Lists all tables (or views) in a specific schema in a specific catalog that the service
     * principal has access to.
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

        return workspaceClient
            .statementExecution()
            .executeStatement(
                ExecuteStatementRequest()
                    .setWarehouseId(config.warehouseId)
                    .apply { if (catalog != null) setCatalog(catalog) }
                    .apply { if (schema != null) setSchema(schema) }
                    .setStatement(statement),
            )
    }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val statement = DatabricksViewGenerator(dataPolicy).toDynamicViewSQL().sql
        val response = executeStatement(statement)
        when (response.status.state) {
            StatementState.SUCCEEDED -> return
            else -> handleDatabricksError(statement, response)
        }
    }
}

class DatabricksTable(
    override val fullName: String,
    private val tableInfo: TableInfo,
    private val databricksClient: DatabricksClient,
) : Table() {
    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    override suspend fun toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy =
        tableInfo.toDataPolicy(platform, getTags(tableInfo))

    /* TODO the current databricks api does not expose column tags! :-(
    The hack we use is to execute a sql query
     */
    private fun getTags(tableInfo: TableInfo): Map<String, List<String>> {

        @Language(value = "Sql")
        val statement =
            """
            SELECT column_name field, tag_name tag, tag_value val
            from system.information_schema.column_tags
            WHERE catalog_name = '${tableInfo.catalogName}'
            AND schema_name = '${tableInfo.schemaName}'
            AND table_name = '${tableInfo.name}'
        """
                .trimIndent()
        val response = databricksClient.executeStatement(statement)
        return when (response.status.state) {
            StatementState.SUCCEEDED -> {
                return response.result.dataArray
                    .orEmpty()
                    .map { it.toList() }
                    .groupBy { it[0] }
                    .mapValues { tags -> tags.value.map { it[1] } }
            }
            else -> {
                if (response.status.error.errorCode != null) {
                    handleDatabricksError(statement, response)
                }
                emptyMap()
            }
        }
    }

    private fun TableInfo.toDataPolicy(
        platform: DataPolicy.ProcessingPlatform,
        tags: Map<String, List<String>>
    ): DataPolicy =
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
                                .addAllTags(tags[column.name] ?: emptyList())
                                .setType(column.typeText)
                                .setRequired(!(column.nullable ?: false))
                                .build()
                                .normalizeType()
                        },
                    )
                    .build(),
            )
            .build()
}

private fun handleDatabricksError(statement: String, response: ExecuteStatementResponse) {
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
