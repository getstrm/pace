package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Lineage
import build.buf.gen.getstrm.pace.api.entities.v1alpha.LineageSummary
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import com.getstrm.pace.config.BigQueryConfiguration
import com.getstrm.pace.domain.LeafResource
import com.getstrm.pace.domain.Resource
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.toTimestamp
import com.getstrm.pace.util.withPageInfo
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.Acl
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryException
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.Dataset as BQDataset
import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.JobId
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.Table as BQTable
import com.google.cloud.bigquery.TableDefinition
import com.google.cloud.bigquery.TableId
import com.google.cloud.datacatalog.lineage.v1.BatchSearchLinkProcessesRequest
import com.google.cloud.datacatalog.lineage.v1.EntityReference
import com.google.cloud.datacatalog.lineage.v1.LineageClient
import com.google.cloud.datacatalog.lineage.v1.LineageSettings
import com.google.cloud.datacatalog.lineage.v1.ProcessLinks
import com.google.cloud.datacatalog.lineage.v1.SearchLinksRequest
import com.google.cloud.datacatalog.v1.PolicyTagManagerClient
import com.google.cloud.datacatalog.v1.PolicyTagManagerSettings
import com.google.cloud.datacatalog.v1.stub.PolicyTagManagerStubSettings
import com.google.rpc.DebugInfo
import org.slf4j.LoggerFactory

typealias LinkName = String

typealias SqlString = String

/**
 * Bigquery has the following mapping between Pace entities and Bigquery entities.
 *
 * PACE: Database -> Schema -> Table
 *
 * BigQuery: Project -> DataSet -> Table
 */
class BigQueryClient(
    override val config: BigQueryConfiguration,
) : ProcessingPlatformClient(config) {

    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    private val credentials: GoogleCredentials =
        GoogleCredentials.fromStream(config.serviceAccountJsonKey.byteInputStream())
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
    private val bigQueryClient: BigQuery =
        BigQueryOptions.newBuilder()
            .setCredentials(credentials)
            .setProjectId(config.projectId)
            .build()
            .apply { this.throwNotFound = true }
            .service
    private val lineageClient =
        LineageClient.create(
            LineageSettings.newBuilder().setCredentialsProvider { credentials }.build()
        )

    private val policyTagManagerSettings: PolicyTagManagerSettings =
        PolicyTagManagerSettings.create(
            PolicyTagManagerStubSettings.newBuilder().setCredentialsProvider { credentials }.build()
        )

    private val polClient: PolicyTagManagerClient =
        PolicyTagManagerClient.create(policyTagManagerSettings)

    private val bigQueryDatabase = BigQueryDatabase()

    /**
     * for now we have interact with only one Gcloud project, and call this the PACE Database. But
     * the access credentials might allow interaction with multiple projects, in which case the
     * result from this call would become greater.
     */
    override suspend fun listChildren(pageParameters: PageParameters): PagedCollection<Resource> =
        listOf(bigQueryDatabase).withPageInfo()

    override suspend fun getChild(childId: String): Resource =
        listOf(bigQueryDatabase).withPageInfo<Resource>().find { it.id == childId }
            ?: throwNotFound(childId, "BigQuery Dataset")

    override suspend fun platformResourceName(index: Int): String =
        listOf("project", "dataset", "table").getOrElse(index) { super.platformResourceName(index) }

    override suspend fun transpilePolicy(dataPolicy: DataPolicy, renderFormatted: Boolean): String {
        return BigQueryViewGenerator(
                dataPolicy,
                config.userGroupsTable,
                config.useIamCheckExtension
            ) {
                if (renderFormatted) {
                    withRenderFormatted(true)
                }
            }
            .toDynamicViewSQL()
            .sql
    }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val query = transpilePolicy(dataPolicy)
        val queryConfig = QueryJobConfiguration.newBuilder(query).setUseLegacySql(false).build()
        try {
            bigQueryClient.query(queryConfig)
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
                log.warn(
                    "Target view(s) for data policy {} already authorized.",
                    dataPolicy.source.ref.integrationFqn
                )
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
        val sourceDataSet =
            bigQueryClient.getDataset(dataPolicy.source.ref.integrationFqn.toTableId().dataset)
        val sourceAcl = sourceDataSet.acl
        val viewsAcl =
            dataPolicy.ruleSetsList.flatMap { ruleSet ->
                // Allow the target view to view the source table
                val targetAcl = Acl.of(Acl.View(ruleSet.target.ref.integrationFqn.toTableId()))
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
        if (config.useIamCheckExtension) {
            log.info("useIamCheckExtension is true, skipping listGroups")
            return emptyList<Group>().withPageInfo()
        }
        // FIXME pagination for listing groups not in place. As the amount of user groups will
        // likely not be ridiculously large, we will fetch them all for now
        val query =
            """
            SELECT
              DISTINCT userGroup
            FROM
              ${config.userGroupsTable}
            ORDER BY userGroup
        """
                .trimIndent()
        val queryConfig = QueryJobConfiguration.newBuilder(query).setUseLegacySql(false).build()
        return bigQueryClient
            .query(queryConfig)
            .iterateAll()
            .map {
                val groupName = it.get("userGroup").stringValue
                Group(id = groupName, name = groupName)
            }
            .withPageInfo()
    }

    override suspend fun createBlueprint(fqn: String): DataPolicy =
        doCreateBlueprint(
            bigQueryClient.getTable(fqn.stripBqPrefix().toTableId())
                ?: throwNotFound(fqn, "BigQuery Table")
        )

    override suspend fun getLineage(request: GetLineageRequest): LineageSummary {
        val (upstream, downstream) = buildLineageList(request.fqn.addBqPrefix())
        return LineageSummary.newBuilder()
            .setResourceRef(
                resourceUrn {
                    integrationFqn = request.fqn
                    platform = apiProcessingPlatform
                }
            )
            .addAllUpstream(upstream)
            .addAllDownstream(downstream)
            .build()
    }

    private fun buildLineageList(fqn: String): Pair<List<Lineage>, List<Lineage>> {
        val bqTable =
            bigQueryClient.getTable(fqn.stripBqPrefix().toTableId())
                ?: throwNotFound(fqn, "BigQuery Table")
        val dataset = bigQueryClient.getDataset(bqTable.tableId.dataset)

        // parent is a concept from the data-catalog/data-lineage api
        // https://cloud.google.com/data-catalog/docs/reference/data-lineage/rest/v1/projects.locations/searchLinks
        // it essentially defines the project and location where your data lives.
        // location can be something continent wide
        val parent = "projects/${config.projectId}/locations/${dataset.location.lowercase()}"
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
        if (upstreamLinks.isEmpty() && downstreamLinks.isEmpty()) {
            return Pair(emptyList(), emptyList())
        }

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
                        ?.let {
                            bigQueryClient.getJob(
                                JobId.newBuilder().setJob(it).setLocation(dataset.location).build()
                            )
                        }
                job?.getConfiguration<QueryJobConfiguration>()?.query
            }

        //  for each link, get the possible sql query from the queryProcessMap
        val queryMap: Map<LinkName, SqlString?> =
            processesMap.mapValues { queryProcessMap[it.value] }

        return Pair(
            upstreamLinks.map { lineageOf(queryMap[it.name], it.source.fullyQualifiedName) },
            downstreamLinks.map { lineageOf(queryMap[it.name], it.target.fullyQualifiedName) }
        )
    }

    private fun lineageOf(relation: String?, fqn: String): Lineage {
        return Lineage.newBuilder()
            .setRelation(relation ?: "unknown")
            .setResourceRef(
                resourceUrn {
                    integrationFqn = fqn.stripBqPrefix()
                    platform = apiProcessingPlatform
                }
            )
            .build()
    }

    /** one BigQueryDatabase corresponds with one Gcloud project */
    private inner class BigQueryDatabase : Resource {
        override val id = config.projectId
        override val displayName = config.projectId

        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> {
            // FIXME pagedCalls function needs to understand page tokens
            // We'll pick that up after the merge of pace-84
            // for now just getting all of the datasets
            // how slow is this?
            val datasets = ArrayList<BQDataset>()
            do {
                val page = bigQueryClient.listDatasets()
                datasets += page.iterateAll()
            } while (page.hasNextPage())
            val info: PagedCollection<Resource> =
                datasets.map { BigQuerySchema(this, it) }.withPageInfo()
            return info
        }

        override suspend fun getChild(childId: String): Resource =
            try {
                BigQuerySchema(this, bigQueryClient.getDataset(childId))
            } catch (e: BigQueryException) {
                throwNotFound(childId, "BigQuery Dataset")
            }

        override fun fqn(): String = this.id
    }

    /** One BigQuerySchema corresponds with one BigQuery dataset */
    private inner class BigQuerySchema(val database: BigQueryDatabase, val dataset: BQDataset) :
        Resource {

        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> {
            // FIXME pagedCalls function needs to understand page tokens
            val tables =
                bigQueryClient.listTables(
                    dataset.datasetId,
                    BigQuery.TableListOption.pageSize(pageParameters.pageSize.toLong()),
                    BigQuery.TableListOption.pageToken(pageParameters.pageToken)
                )
            return tables.values
                .toList()
                .map { BigQueryTable(this, it) }
                .withPageInfo(tables.nextPageToken)
        }

        override suspend fun getChild(childId: String): BigQueryTable =
            try {
                BigQueryTable(
                    this,
                    bigQueryClient.getTable(TableId.of(dataset.datasetId.dataset, childId))
                )
            } catch (e: BigQueryException) {
                throwNotFound(childId, "BigQuery Table")
            }

        override val id: String
            get() = dataset.datasetId.dataset

        override val displayName: String
            get() = id

        override fun fqn(): String = "${database.id}.$id"
    }

    /** One BigQueryTable corresponds with one PACE Table. */
    private inner class BigQueryTable(
        val schema: BigQuerySchema,
        private val bqTable: BQTable,
    ) : LeafResource() {
        override suspend fun createBlueprint() = doCreateBlueprint(bqTable)

        override val id: String = bqTable.tableId.table
        override val displayName = id

        override fun fqn(): String = "${schema.fqn()}.${id}"
    }

    private fun doCreateBlueprint(bqTable: com.google.cloud.bigquery.Table): DataPolicy {
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
                    .setTitle(table.fullName())
                    .setDescription(table.description.orEmpty())
                    .setCreateTime(table.creationTime.toTimestamp())
                    .setUpdateTime(table.lastModifiedTime.toTimestamp()),
            )
            .setSource(
                DataPolicy.Source.newBuilder()
                    .setRef(
                        resourceUrn {
                            integrationFqn = table.fullName()
                            platform = apiProcessingPlatform
                        }
                    )
                    .addAllFields(
                        table.getDefinition<TableDefinition>().schema?.fields.orEmpty().map { field
                            ->
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
}

private fun BQTable.fullName() = with(tableId) { "${project}.${dataset}.${table}" }

private const val BIGQUERY_PREFIX = "bigquery:"

private fun String.addBqPrefix() =
    if (!this.startsWith(BIGQUERY_PREFIX)) "${BIGQUERY_PREFIX}$this" else this

private fun String.stripBqPrefix(): String =
    if (this.startsWith(BIGQUERY_PREFIX))
        this.subSequence(BIGQUERY_PREFIX.length, this.length).toString()
    else this

private fun String.toEntity() = EntityReference.newBuilder().setFullyQualifiedName(this).build()

private fun String.toTableId(): TableId {
    val (project, dataset, table) = replace("`", "").split(".")
    return TableId.of(project, dataset, table)
}
