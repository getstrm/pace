package com.getstrm.pace.bigquery

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.BIGQUERY
import com.getstrm.pace.config.BigQueryConfig
import com.getstrm.pace.domain.Group
import com.getstrm.pace.domain.ProcessingPlatformInterface
import com.getstrm.pace.domain.Table
import com.getstrm.pace.exceptions.InternalException
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.*
import com.google.rpc.DebugInfo
import org.slf4j.LoggerFactory
import toFullName
import com.google.cloud.bigquery.Table as BQTable

class BigQueryClient(
    override val id: String,
    serviceAccountKeyJson: String,
    projectId: String,
    private val userGroupsTable: String,
) : ProcessingPlatformInterface {
    constructor(config: BigQueryConfig) : this(
        config.id,
        config.serviceAccountKeyJson,
        config.projectId,
        config.userGroupsTable,
    )

    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    private val credentials: GoogleCredentials
    private val bigQuery: BigQuery

    init {
        // Create a service account credential.
        credentials = GoogleCredentials.fromStream(serviceAccountKeyJson.byteInputStream())
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
        bigQuery = BigQueryOptions.newBuilder()
            .setCredentials(credentials)
            .setProjectId(projectId)
            .build()
            .service
    }

    override suspend fun listTables(): List<Table> {
        val dataSets = bigQuery.listDatasets().iterateAll()
        return dataSets.flatMap { dataSet ->
            bigQuery.listTables(dataSet.datasetId).iterateAll().toList()
        }.map { table ->
            BigQueryTable(table.tableId.toFullName(), table)
        }
    }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val viewGenerator = BigQueryDynamicViewGenerator(dataPolicy, userGroupsTable)
        val query = viewGenerator.toDynamicViewSQL()
        val queryConfig = QueryJobConfiguration.newBuilder(query)
            .setUseLegacySql(false)
            .build()
        try {
            bigQuery.query(queryConfig)
        } catch (e: JobException) {
            log.warn("SQL query\n{}", query)
            log.warn("Caused error {}", e.message)
            throw InternalException(
                InternalException.Code.INTERNAL,
                DebugInfo.newBuilder()
                    .setDetail(
                        "Error while executing BigQuery query (error message: ${e.message}), please check the logs of your PACE deployment. This is a bug, please report to https://github.com/getstrm/pace/issues/new"
                    )
                    .addAllStackEntries(e.stackTrace.map { it.toString() })
                    .build(),
                e
            )
        }
        try {
            authorizeViews(dataPolicy)
        } catch (e: BigQueryException) {
            if (e.message == "Duplicate authorized views") {
                log.debug("{}", e.message)
            } else {
                throw e
            }
        }
    }

    // TODO bombs when running twice!
    private fun authorizeViews(dataPolicy: DataPolicy) {
        val sourceDataSet = bigQuery.getDataset(dataPolicy.source.ref.toTableId().dataset)
        val sourceAcl = sourceDataSet.acl
        val viewsAcl = dataPolicy.ruleSetsList.map {
            Acl.of(Acl.View(it.target.fullname.toTableId()))
        }
        sourceDataSet.toBuilder().setAcl(sourceAcl + viewsAcl).build().update()
    }

    override val type
        get() = BIGQUERY

    override suspend fun listGroups(): List<Group> {
        val query = """
            SELECT
              DISTINCT userGroup
            FROM
              $userGroupsTable
        """.trimIndent()
        val queryConfig = QueryJobConfiguration.newBuilder(query)
            .setUseLegacySql(false)
            .build()
        return bigQuery.query(queryConfig).iterateAll().map {
            val groupName = it.get("userGroup").stringValue
            Group(id = groupName, name = groupName)
        }
    }

    companion object {
        private fun String.toTableId(): TableId {
            val (project, dataset, table) = replace("`", "").split(".")
            return TableId.of(project, dataset, table)
        }
    }
}

class BigQueryTable(
    override val fullName: String,
    private val table: BQTable,
) : Table() {

    override suspend fun toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy =
        table.toDataPolicy(platform)
}
