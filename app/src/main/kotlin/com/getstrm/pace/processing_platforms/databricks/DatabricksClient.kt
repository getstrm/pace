package com.getstrm.pace.processing_platforms.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
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
import com.getstrm.pace.domain.LeafResource
import com.getstrm.pace.domain.Resource
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.MILLION_RECORDS
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

    override suspend fun platformResourceName(index: Int): String =
        listOf("catalog", "schema", "table").getOrElse(index) { super.platformResourceName(index) }

    override suspend fun listChildren(pageParameters: PageParameters): PagedCollection<Resource> =
        listDatabases(pageParameters)

    override suspend fun getChild(childId: String): Resource =
        listDatabases(MILLION_RECORDS).find { it.id == childId }
            ?: throwNotFound(childId, "Databricks database")

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

    fun listDatabases(pageParameters: PageParameters): PagedCollection<Resource> =
        workspaceClient
            .catalogs()
            .list()
            .applyPageParameters(pageParameters)
            .map { catalogInfo -> DatabricksDatabase(catalogInfo.name) }
            .withPageInfo()

    fun listSchemas(databaseId: String): PagedCollection<Resource> =
        try {
            workspaceClient
                .schemas()
                .list(databaseId)
                .map { schemaInfo ->
                    val database = DatabricksDatabase(schemaInfo.catalogName)
                    DatabricksSchema(database, schemaInfo.name)
                }
                .withPageInfo()
        } catch (t: Throwable) {
            mapNotFoundError(t, "Databricks catalog", databaseId)
        }

    fun listTables(databaseId: String, schemaId: String): PagedCollection<Resource> =
        try {
            workspaceClient
                .tables()
                .list(databaseId, schemaId)
                .map { tableInfo -> tableInfo.toTable() }
                .withPageInfo()
        } catch (t: Throwable) {
            mapNotFoundError(t, "Databricks schema", schemaId)
        }

    private fun getTable(databaseId: String, schemaId: String, tableId: String): DatabricksTable =
        try {
            workspaceClient.tables().get("$databaseId.$schemaId.$tableId").toTable()
        } catch (t: Throwable) {
            mapNotFoundError(t, "Databricks table", tableId)
        }

    private fun TableInfo.toTable(): DatabricksTable {
        val database = DatabricksDatabase(catalogName)
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

    inner class DatabricksDatabase(name: String) : Resource {
        override val id = name
        override val displayName = id

        override fun fqn(): String = id

        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> = listSchemas(id)

        override suspend fun getChild(childId: String): Resource =
            try {
                workspaceClient.schemas().get("${fqn()}.$childId").let {
                    DatabricksSchema(this, it.name)
                }
            } catch (t: Throwable) {
                mapNotFoundError(t, "Databricks schema", childId)
            }
    }

    inner class DatabricksSchema(val database: DatabricksDatabase, name: String) : Resource {

        override val id = name
        override val displayName = name

        override fun fqn(): String = "${database.id}.$id"

        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> = listTables(database.id, displayName)

        override suspend fun getChild(childId: String): Resource =
            getTable(database.id, displayName, childId)
    }

    inner class DatabricksTable(
        val schema: DatabricksSchema,
        private val tableInfo: TableInfo,
    ) : LeafResource() {

        override val id: String = tableInfo.name
        override val displayName = id

        override fun fqn(): String = "${schema.fqn()}.${id}"

        override suspend fun createBlueprint(): DataPolicy =
            tableInfo.toDataPolicy(getTags(tableInfo))

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
