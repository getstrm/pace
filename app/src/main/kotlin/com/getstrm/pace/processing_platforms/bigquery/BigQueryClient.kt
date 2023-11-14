package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.BIGQUERY
import com.getstrm.pace.config.BigQueryConfig
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.processing_platforms.Table
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.toFullName
import com.getstrm.pace.util.toTimestamp
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.*
import com.google.cloud.datacatalog.v1.PolicyTagManagerClient
import com.google.rpc.DebugInfo
import org.slf4j.LoggerFactory
import com.google.cloud.bigquery.Table as BQTable

class BigQueryClient(
    override val id: String,
    serviceAccountKeyJson: String,
    private val projectId: String,
    private val userGroupsTable: String,
) : ProcessingPlatformClient {
    constructor(config: BigQueryConfig) : this(
        config.id,
        config.serviceAccountJsonKey,
        config.projectId,
        config.userGroupsTable,
    )

    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    private val credentials: GoogleCredentials
    private val bigQuery: BigQuery
    private val polClient: PolicyTagManagerClient

    init {
        // Create a service account credential.
        credentials = GoogleCredentials.fromStream(serviceAccountKeyJson.byteInputStream())
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
        bigQuery = BigQueryOptions.newBuilder()
            .setCredentials(credentials)
            .setProjectId(projectId)
            .build()
            .service
        polClient = PolicyTagManagerClient.create()
    }

    override suspend fun listTables(): List<Table> {
        val dataSets = bigQuery.listDatasets().iterateAll()
        return dataSets.flatMap { dataSet ->
            bigQuery.listTables(dataSet.datasetId).iterateAll().toList()
        }.map { table ->
            BigQueryTable(table.tableId.toFullName(), table, polClient)
        }
    }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val viewGenerator = BigQueryViewGenerator(dataPolicy, userGroupsTable)
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
                        "Error while executing BigQuery query (error message: ${e.message}), please check the logs of your PACE deployment. $BUG_REPORT",
                    )
                    .addAllStackEntries(e.stackTrace.map { it.toString() })
                    .build(),
                e,
            )
        }
        try {
            authorizeViews(dataPolicy)
        } catch (e: BigQueryException) {
            if (e.message == "Duplicate authorized views") {
                log.warn("Target view(s) for data policy {} already authorized.", dataPolicy.id)
            } else {
                throw InternalException(
                    InternalException.Code.INTERNAL,
                    DebugInfo.newBuilder()
                        .setDetail(
                            "Error while authorizing views (error message: ${e.message}), please check the logs of your PACE deployment. $BUG_REPORT",
                        )
                        .addAllStackEntries(e.stackTrace.map { it.toString() })
                        .build(),
                    e,
                )
            }
        }
    }

    // Fixme: better handle case where view was already authorized (currently caught above)
    private fun authorizeViews(dataPolicy: DataPolicy) {
        val sourceDataSet = bigQuery.getDataset(dataPolicy.source.ref.toTableId().dataset)
        val sourceAcl = sourceDataSet.acl
        val viewsAcl = dataPolicy.ruleSetsList.flatMap { ruleSet ->
            // Allow the target view to view the source table
            val targetAcl = Acl.of(Acl.View(ruleSet.target.fullname.toTableId()))
            // Allow the target view to view any applicable token source tables
            val tokenSourceAcls = ruleSet.fieldTransformsList.flatMap { fieldTransform ->
                fieldTransform.transformsList.mapNotNull { transform ->
                    if (transform.hasDetokenize()) {
                        Acl.of(Acl.View(transform.detokenize.tokenSourceRef.toTableId()))
                    } else {
                        null
                    }
                }
            }
            tokenSourceAcls + targetAcl
        }
        sourceDataSet.toBuilder().setAcl(sourceAcl + viewsAcl).build().update()
    }

    override val type = BIGQUERY

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
    private val table: com.google.cloud.bigquery.Table,
    private val polClient: PolicyTagManagerClient,
) : Table() {

    override suspend fun toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy =
        table.toDataPolicy(platform)


    private fun BQTable.toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy {
        // The reload ensures all metadata is fetched, including the schema
        val table = reload()
        // typical tag value =
        // projects/stream-machine-development/locations/europe-west4/taxonomies/5886429027103247004/policyTags/5980433277276442728
        var tags = table.getDefinition<TableDefinition>().schema?.fields.orEmpty().map {
            it.name to (it.policyTags?.names ?: emptyList())
        }.toMap()
        val nameLookup = tags.values.flatten().toSet().associateWith { tag: String ->
            polClient.getPolicyTag(tag).displayName
        }
        tags = tags.mapValues { (_, v) -> v.map { nameLookup[it] } }

        return DataPolicy.newBuilder()
            .setMetadata(
                DataPolicy.Metadata.newBuilder()
                    .setTitle(this.toFullName())
                    .setDescription(table.description.orEmpty())
                    .setCreateTime(table.creationTime.toTimestamp())
                    .setUpdateTime(table.lastModifiedTime.toTimestamp()),
            )
            .setPlatform(platform)
            .setSource(
                DataPolicy.Source.newBuilder()
                    .setRef(this.toFullName())
                    .addAllFields(
                        table.getDefinition<TableDefinition>().schema?.fields.orEmpty().map { field ->
                            // Todo: add support for nested fields using getSubFields()
                            DataPolicy.Field.newBuilder()
                                .addNameParts(field.name)
                                .addAllTags(tags[field.name])
                                .setType(field.type.name())
                                // Todo: correctly handle repeated fields (defined by mode REPEATED)
                                .setRequired(field.mode != Field.Mode.NULLABLE)
                                .build().normalizeType()
                        },
                    )
                    .build(),
            )
            .build()
    }

}
