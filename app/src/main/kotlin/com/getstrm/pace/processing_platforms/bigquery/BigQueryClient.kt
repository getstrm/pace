package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Lineage
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform.PlatformType.BIGQUERY
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageResponse
import com.getstrm.pace.config.BigQueryConfig
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.*
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.*
import com.google.cloud.bigquery.Dataset as BQDataset
import com.google.cloud.bigquery.Table as BQTable
import com.google.cloud.datacatalog.lineage.v1.BatchSearchLinkProcessesRequest
import com.google.cloud.datacatalog.lineage.v1.EntityReference
import com.google.cloud.datacatalog.lineage.v1.LineageClient
import com.google.cloud.datacatalog.lineage.v1.LineageSettings
import com.google.cloud.datacatalog.lineage.v1.ProcessLinks
import com.google.cloud.datacatalog.lineage.v1.SearchLinksRequest
import com.google.cloud.datacatalog.v1.PolicyTagManagerClient
import com.google.rpc.DebugInfo
import org.slf4j.LoggerFactory

typealias LinkName = String

typealias SqlString = String
/**
 * Bigquery has the following mapping between Pace entities and Bigquery entities.
 *
 * PACE BigQuery Database Project Schema Dataset Table Table
 */
class BigQueryClient(
    override val config: BigQueryConfig,
) : ProcessingPlatformClient(config) {

    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    // Create a service account credential.
    private val credentials: GoogleCredentials =
        GoogleCredentials.fromStream(config.serviceAccountJsonKey.byteInputStream())
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
    private val bigQuery: BigQuery =
        BigQueryOptions.newBuilder()
            .setCredentials(credentials)
            .setProjectId(config.projectId)
            .build()
            .service
    private val lineageClient =
        LineageClient.create(
            LineageSettings.newBuilder().setCredentialsProvider { credentials }.build()
        )
    // TODO determine eu or us!
    val parent = "projects/${config.projectId}/locations/us"

    private val polClient: PolicyTagManagerClient = PolicyTagManagerClient.create()

    private val bigQueryDatabase = BigQueryDatabase(this, config.projectId)
    /**
     * for now we have interact with only one Gcloud project, and call this the PACE Database. But
     * the access credentials might allow interaction with multiple projects, in which case the
     * result from this call would become greater.
     */
    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<Database> =
        listOf(bigQueryDatabase).withPageInfo()

    private suspend fun getDatabase(databaseId: String): Database =
        listDatabases(DEFAULT_PAGE_PARAMETERS).find { it.id == databaseId }
            ?: throwNotFound(databaseId, "BigQuery Dataset")

    override suspend fun listSchemas(databaseId: String, pageParameters: PageParameters) =
        getDatabase(databaseId).listSchemas(pageParameters)

    override suspend fun listTables(
        databaseId: String,
        schemaId: String,
        pageParameters: PageParameters
    ) = getDatabase(databaseId).getSchema(schemaId).listTables(pageParameters)

    override suspend fun getTable(databaseId: String, schemaId: String, tableId: String) =
        getDatabase(databaseId).getSchema(schemaId).getTable(tableId)

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val viewGenerator = BigQueryViewGenerator(dataPolicy, config.userGroupsTable)
        val query = viewGenerator.toDynamicViewSQL().sql
        val queryConfig = QueryJobConfiguration.newBuilder(query).setUseLegacySql(false).build()
        try {
            bigQuery.query(queryConfig)
        } catch (e: Exception) {
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
        val viewsAcl =
            dataPolicy.ruleSetsList.flatMap { ruleSet ->
                // Allow the target view to view the source table
                val targetAcl = Acl.of(Acl.View(ruleSet.target.fullname.toTableId()))
                // Allow the target view to view any applicable token source tables
                val tokenSourceAcls =
                    ruleSet.fieldTransformsList.flatMap { fieldTransform ->
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

    override suspend fun listGroups(pageParameters: PageParameters): PagedCollection<Group> {
        val query =
            """
            SELECT
              DISTINCT userGroup
            FROM
              ${config.userGroupsTable}
            ORDER BY userGroup
            LIMIT ${pageParameters.pageSize}
            OFFSET ${pageParameters.skip}
        """
                .trimIndent()
        val queryConfig = QueryJobConfiguration.newBuilder(query).setUseLegacySql(false).build()
        return bigQuery
            .query(queryConfig)
            .iterateAll()
            .map {
                val groupName = it.get("userGroup").stringValue
                Group(id = groupName, name = groupName)
            }
            .withPageInfo()
    }

    override fun getLineage(request: GetLineageRequest): GetLineageResponse {
        val (upstream, downstream) = buildLineageList(request.fqn.addBqPrefix())
        return GetLineageResponse.newBuilder()
            .addAllUpstream(upstream)
            .addAllDownstream(downstream)
            .build()
    }

    private fun buildLineageList(fqn: String): Pair<List<Lineage>, List<Lineage>> {
        val downstreamLinks =
            lineageClient
                .searchLinks(
                    SearchLinksRequest.newBuilder()
                        .setParent(parent)
                        .setSource(fqn.toEntity())
                        .build()
                )
                // TODO handle multiple response pages
                .page
                .values
                .toList()
        val upstreamLinks =
            lineageClient
                .searchLinks(
                    SearchLinksRequest.newBuilder()
                        .setParent(parent)
                        .setTarget(fqn.toEntity())
                        .build()
                )
                // TODO handle multiple response pages
                .page
                .values
                .toList()

        // find the processes associated with the found links. Note that one process
        // can cause multiple links, which is why we invert the map, and flatten the list.
        val processesMap: Map<LinkName, ProcessLinks> =
            lineageClient
                .batchSearchLinkProcesses(
                    BatchSearchLinkProcessesRequest.newBuilder()
                        .addAllLinks((downstreamLinks + upstreamLinks).map { it.name })
                        .setParent(parent)
                        .build()
                )
                // TODO handle multi-page responses
                .page
                .values
                .map { processLink -> processLink.linksList.map { it.link to processLink } }
                .flatten()
                .toMap()

        // for each process we find a possible associated SQL query.
        // the advantage of this two-step approach is that we don't have to call
        // BigQuery twice for the same SQL.
        val queryProcessMap: Map<ProcessLinks, SqlString?> =
            processesMap.values.toSet().associateWith { processLinks ->
                val job =
                    lineageClient
                        .getProcess(processLinks.process)
                        .attributesMap["bigquery_job_id"]
                        ?.stringValue
                        ?.let { bigQuery.getJob(it) }
                job?.getConfiguration<QueryJobConfiguration>()?.query
            }

        //  for each link, get the possible sql query from the queryProcessMap
        val queryMap: Map<LinkName, SqlString?> =
            processesMap.mapValues { queryProcessMap[it.value] }

        return Pair(
            upstreamLinks.map { makeLineage(queryMap[it.name], it.source.fullyQualifiedName) },
            downstreamLinks.map { makeLineage(queryMap[it.name], it.target.fullyQualifiedName) }
        )
    }

    private fun makeLineage(relation: String?, fqn: String): Lineage {
        return Lineage.newBuilder().setRelation(relation ?: "unknown").setFqn(fqn).build()
    }

    companion object {
        private fun String.toTableId(): TableId {
            val (project, dataset, table) = replace("`", "").split(".")
            return TableId.of(project, dataset, table)
        }
    }

    /** one BigQueryDatabase corresponds with one Gcloud project */
    inner class BigQueryDatabase(platformClient: ProcessingPlatformClient, projectId: String) :
        Database(
            platformClient = platformClient,
            id = projectId,
            dbType = BIGQUERY.name,
            displayName = projectId
        ) {

        override suspend fun listSchemas(pageParameters: PageParameters): PagedCollection<Schema> {
            // FIXME pagedCalls function needs to understand page tokens
            // We'll pick that up after the merge of pace-84
            // for now just getting all of the datasets
            // how slow is this?
            val datasets = ArrayList<BQDataset>()
            do {
                val page = bigQuery.listDatasets()
                datasets += page.iterateAll()
            } while (page.hasNextPage())
            val info: PagedCollection<Schema> =
                datasets.map { BigQuerySchema(this, it) }.withPageInfo()
            return info
        }

        override suspend fun getSchema(schemaId: String): Schema =
            BigQuerySchema(this, bigQuery.getDataset(schemaId))
    }

    /** One BigQuerySchema corresponds with one BigQuery dataset */
    inner class BigQuerySchema(database: BigQueryDatabase, val dataset: BQDataset) :
        Schema(database, dataset.datasetId.dataset, dataset.datasetId.dataset) {

        override suspend fun listTables(pageParameters: PageParameters): PagedCollection<Table> =
            // FIXME pagedCalls function needs to understand page tokens
            bigQuery
                .listTables(dataset.datasetId)
                .iterateAll()
                .toList()
                .applyPageParameters(pageParameters)
                .map { BigQueryTable(this, it) }
                .withPageInfo()

        override suspend fun getTable(tableId: String) =
            BigQueryTable(this, bigQuery.getTable(TableId.of(dataset.datasetId.dataset, tableId)))
    }

    /** One BigQueryTable corresponds with one PACE Table. */
    inner class BigQueryTable(
        schema: Schema,
        private val bqTable: BQTable,
    ) : Table(schema, bqTable.tableId.table, bqTable.generatedId) {

        override suspend fun createBlueprint(): DataPolicy {
            // The reload ensures all metadata is fetched, including the schema
            val table = bqTable.reload()
            // typical tag value =
            // projects/stream-machine-development/locations/europe-west4/taxonomies/5886429027103247004/policyTags/5980433277276442728
            var tags =
                table.getDefinition<TableDefinition>().schema?.fields.orEmpty().associate {
                    it.name to (it.policyTags?.names ?: emptyList())
                }
            val nameLookup =
                tags.values.flatten().toSet().associateWith { tag: String ->
                    polClient.getPolicyTag(tag).displayName
                }
            tags = tags.mapValues { (_, v) -> v.map { nameLookup[it] } }

            return DataPolicy.newBuilder()
                .setMetadata(
                    DataPolicy.Metadata.newBuilder()
                        .setTitle(this.fullName)
                        .setDescription(table.description.orEmpty())
                        .setCreateTime(table.creationTime.toTimestamp())
                        .setUpdateTime(table.lastModifiedTime.toTimestamp()),
                )
                .setPlatform(apiPlatform)
                .setSource(
                    DataPolicy.Source.newBuilder()
                        .setRef(this.fullName)
                        .addAllFields(
                            table.getDefinition<TableDefinition>().schema?.fields.orEmpty().map {
                                field ->
                                // Todo: add support for nested fields using getSubFields()
                                DataPolicy.Field.newBuilder()
                                    .addNameParts(field.name)
                                    .addAllTags(tags[field.name])
                                    .setType(field.type.name())
                                    // Todo: correctly handle repeated fields (defined by mode
                                    // REPEATED)
                                    .setRequired(field.mode != Field.Mode.NULLABLE)
                                    .build()
                                    .normalizeType()
                            },
                        )
                )
                .build()
        }

        override val fullName = with(bqTable.tableId) { "${project}.${dataset}.${table}" }
    }
}

private fun String.addBqPrefix() = if (!this.startsWith("bigquery:")) "bigquery:$this" else this

private fun String.toEntity() = EntityReference.newBuilder().setFullyQualifiedName(this).build()
