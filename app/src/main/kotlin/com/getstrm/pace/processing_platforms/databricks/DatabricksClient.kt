package com.getstrm.pace.processing_platforms.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform.PlatformType.DATABRICKS
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.databricks.sdk.AccountClient
import com.databricks.sdk.WorkspaceClient
import com.databricks.sdk.core.DatabricksConfig as DatabricksClientConfig
import com.databricks.sdk.core.DatabricksError
import com.databricks.sdk.service.catalog.TableInfo
import com.databricks.sdk.service.iam.ListAccountGroupsRequest
import com.databricks.sdk.service.sql.ExecuteStatementRequest
import com.databricks.sdk.service.sql.ExecuteStatementResponse
import com.databricks.sdk.service.sql.StatementState
import com.getstrm.pace.config.DatabricksConfiguration
import com.getstrm.pace.domain.Resource
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.applyPageParameters
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.toTimestamp
import com.getstrm.pace.util.withPageInfo
import com.google.rpc.DebugInfo
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(DatabricksClient::javaClass.name)

class DatabricksClient(
    override val config: DatabricksConfiguration,
) : ProcessingPlatformClient(config) {

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

    override suspend fun platformResourceName(index: Int): String {
        return when (index) {
            1 -> "catalog"
            2 -> "schema"
            3 -> "table"
            else -> "Level-$index"
        }
    }

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

    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<Resource> {
        return workspaceClient
            .catalogs()
            .list()
            .applyPageParameters(pageParameters)
            .map { catalogInfo -> DatabricksDatabase(this, catalogInfo.name) }
            .withPageInfo()
    }

    override suspend fun listSchemas(
        databaseId: String,
        pageParameters: PageParameters
    ): PagedCollection<Resource> {
        try {
            return workspaceClient
                .schemas()
                .list(databaseId)
                .map { schemaInfo ->
                    val database = DatabricksDatabase(this, schemaInfo.catalogName)
                    DatabricksSchema(database, schemaInfo.name)
                }
                .withPageInfo()
        } catch (t: Throwable) {
            mapNotFoundError(t, "Databricks catalog", databaseId)
        }
    }

    override suspend fun listTables(
        databaseId: String,
        schemaId: String,
        pageParameters: PageParameters
    ): PagedCollection<Resource> {
        try {
            return workspaceClient
                .tables()
                .list(databaseId, schemaId)
                .map { tableInfo -> tableInfo.toTable() }
                .withPageInfo()
        } catch (t: Throwable) {
            mapNotFoundError(t, "Databricks schema", schemaId)
        }
    }

    override suspend fun getTable(databaseId: String, schemaId: String, tableId: String): Table {
        try {
            return workspaceClient.tables().get("$databaseId.$schemaId.$tableId").toTable()
        } catch (t: Throwable) {
            mapNotFoundError(t, "Databricks table", tableId)
        }
    }

    private fun TableInfo.toTable(): DatabricksTable {
        val database = DatabricksDatabase(this@DatabricksClient, catalogName)
        val schema = DatabricksSchema(database, schemaName)
        return DatabricksTable(schema, this)
    }

    private fun mapNotFoundError(
        t: Throwable,
        resourceType: String,
        resourceName: String
    ): Nothing {
        if (t is DatabricksError && t.isMissing) {
            throwNotFound(resourceName, resourceType)
        } else {
            throw t
        }
    }

    inner class DatabricksDatabase(
        processingPlatformClient: ProcessingPlatformClient,
        name: String
    ) : Database(processingPlatformClient, name, DATABRICKS) {
        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> = listSchemas(id, pageParameters)

        override suspend fun getChild(childId: String): Resource {
            try {
                return workspaceClient.schemas().get(childId).let {
                    DatabricksSchema(this, it.name)
                }
            } catch (t: Throwable) {
                mapNotFoundError(t, "Databricks schema", childId)
            }
        }

        override fun fqn(): String {
            return id
        }
    }

    inner class DatabricksSchema(database: Database, name: String) :
        Schema(
            database = database,
            id = name,
            displayName = name,
        ) {
        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> = listTables(database.id, displayName, pageParameters)

        override suspend fun getChild(tableId: String): Table {
            getTable(database.id, displayName, tableId).let {
                return it
            }
        }

        override fun fqn(): String {
            return "${database.id}.$id"
        }
    }

    inner class DatabricksTable(
        schema: Schema,
        private val tableInfo: TableInfo,
        override val fullName: String = tableInfo.fullName,
    ) :
        Table(
            schema = schema,
            id = tableInfo.name,
            displayName = tableInfo.name,
        ) {
        private val log by lazy { LoggerFactory.getLogger(javaClass) }

        override suspend fun createBlueprint(): DataPolicy {
            return tableInfo.toDataPolicy(getTags(tableInfo))
        }

        override fun fqn(): String {
            return "${schema.fqn()}.${id}"
        }

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
            val response = executeStatement(statement)
            return when (response.status.state) {
                StatementState.SUCCEEDED -> {
                    return response.result.dataArray
                        .orEmpty()
                        .map { it.toList() }
                        .groupBy { it[0] }
                        .mapValues { tags -> tags.value.map { it[1] } }
                }
                StatementState.CANCELED -> TODO()
                StatementState.CLOSED -> TODO()
                StatementState.FAILED -> TODO()
                StatementState.PENDING -> {
                    // TODO no compute!
                    // https://github.com/getstrm/pace/issues/134
                    throw InternalException(
                        InternalException.Code.INTERNAL,
                        DebugInfo.newBuilder().setDetail("Databricks query is pending").build()
                    )
                }
                StatementState.RUNNING -> TODO()
                else -> {
                    if (response.status.error.errorCode != null) {
                        handleDatabricksError(statement, response)
                    }
                    emptyMap()
                }
            }
        }

        private fun TableInfo.toDataPolicy(tags: Map<String, List<String>>): DataPolicy =
            DataPolicy.newBuilder()
                .setMetadata(
                    DataPolicy.Metadata.newBuilder()
                        .setTitle(name)
                        .setDescription(comment.orEmpty())
                        .setCreateTime(createdAt.toTimestamp())
                        .setUpdateTime(updatedAt.toTimestamp()),
                )
                .setSource(
                    DataPolicy.Source.newBuilder()
                        .setRef(
                            resourceUrn {
                                integrationFqn = fullName
                                platform = apiProcessingPlatform
                            }
                        )
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
            "Databricks response %s: %s"
                .format(response.status.error, response.status.error.message)
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
