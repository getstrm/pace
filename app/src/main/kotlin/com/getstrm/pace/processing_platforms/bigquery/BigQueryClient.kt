package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataResourceRef
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Lineage
import build.buf.gen.getstrm.pace.api.entities.v1alpha.LineageSummary
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform.PlatformType.BIGQUERY
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import com.getstrm.pace.config.BigQueryConfig
import com.getstrm.pace.domain.Level1
import com.getstrm.pace.domain.Level2
import com.getstrm.pace.domain.Level3
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.DEFAULT_PAGE_PARAMETERS
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.applyPageParameters
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.toTimestamp
import com.getstrm.pace.util.withPageInfo
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
 * PACE: Database -> Schema -> Table
 *
 * BigQuery: Project -> DataSet -> Table
 */
class BigQueryClient(
    override val config: BigQueryConfig,
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
            .service
    private val lineageClient =
        LineageClient.create(
            LineageSettings.newBuilder().setCredentialsProvider { credentials }.build()
        )

    private val polClient: PolicyTagManagerClient = PolicyTagManagerClient.create()

    private val bigQueryDatabase = BigQueryDatabase(this, config.projectId)

    override suspend fun platformResourceName(index: Int): String {
        return when (index) {
            0 -> "BigQuery"
            1 -> "project"
            2 -> "dataset"
            3 -> "table"
            else -> throw IllegalArgumentException("Unsupported index: $index")
        }
    }

    /**
     * for now we have interact with only one Gcloud project, and call this the PACE Database. But
     * the access credentials might allow interaction with multiple projects, in which case the
     * result from this call would become greater.
     */
    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<Level1> =
        listOf(bigQueryDatabase).withPageInfo()

    private suspend fun getDatabase(databaseId: String): Level1 =
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
                    dataPolicy.source.ref.platformFqn
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
            bigQueryClient.getDataset(dataPolicy.source.ref.platformFqn.toTableId().dataset)
        val sourceAcl = sourceDataSet.acl
        val viewsAcl =
            dataPolicy.ruleSetsList.flatMap { ruleSet ->
                // Allow the target view to view the source table
                val targetAcl = Acl.of(Acl.View(ruleSet.target.ref.platformFqn.toTableId()))
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
        return bigQueryClient
            .query(queryConfig)
            .iterateAll()
            .map {
                val groupName = it.get("userGroup").stringValue
                Group(id = groupName, name = groupName)
            }
            .withPageInfo()
    }

    override fun createBlueprint(fqn: String): DataPolicy =
        doCreateBlueprint(
            bigQueryClient.getTable(fqn.stripBqPrefix().toTableId())
                ?: throwNotFound(fqn, "BigQuery Table")
        )

    override suspend fun getLineage(request: GetLineageRequest): LineageSummary {
        val (upstream, downstream) = buildLineageList(request.fqn.addBqPrefix())
        return LineageSummary.newBuilder()
            .setResourceRef(
                DataResourceRef.newBuilder()
                    .setPlatformFqn(request.fqn)
                    .setPaceUrn(ResourceUrn.newBuilder().setPlatformFqn(request.fqn).build())
                    .setPlatform(apiProcessingPlatform)
                    .build()
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
                DataResourceRef.newBuilder()
                    .setPlatformFqn(fqn.stripBqPrefix())
                    .setPaceUrn(
                        ResourceUrn.newBuilder().setPlatformFqn(fqn.stripBqPrefix()).build()
                    )
                    .setPlatform(apiProcessingPlatform)
                    .build()
            )
            .build()
    }

    /** one BigQueryDatabase corresponds with one Gcloud project */
    inner class BigQueryDatabase(platformClient: ProcessingPlatformClient, projectId: String) :
        Database(platformClient, projectId, BIGQUERY) {

        override suspend fun listSchemas(pageParameters: PageParameters): PagedCollection<Level2> {
            // FIXME pagedCalls function needs to understand page tokens
            // We'll pick that up after the merge of pace-84
            // for now just getting all of the datasets
            // how slow is this?
            val datasets = ArrayList<BQDataset>()
            do {
                val page = bigQueryClient.listDatasets()
                datasets += page.iterateAll()
            } while (page.hasNextPage())
            val info: PagedCollection<Level2> =
                datasets.map { BigQuerySchema(this, it) }.withPageInfo()
            return info
        }

        override suspend fun getSchema(schemaId: String): Schema =
            BigQuerySchema(this, bigQueryClient.getDataset(schemaId))

        override fun fqn(): String {
            return this.id
        }
    }

    /** One BigQuerySchema corresponds with one BigQuery dataset */
    inner class BigQuerySchema(database: BigQueryDatabase, val dataset: BQDataset) :
        Schema(database, dataset.datasetId.dataset, dataset.datasetId.dataset) {

        override suspend fun listTables(pageParameters: PageParameters): PagedCollection<Level3> =
            // FIXME pagedCalls function needs to understand page tokens
            bigQueryClient
                .listTables(dataset.datasetId)
                .iterateAll()
                .toList()
                .applyPageParameters(pageParameters)
                .map { BigQueryTable(this, it) }
                .withPageInfo()

        override suspend fun getTable(tableId: String) =
            BigQueryTable(
                this,
                bigQueryClient.getTable(TableId.of(dataset.datasetId.dataset, tableId))
            )

        override fun fqn(): String {
            return "${database.id}.$id"
        }
    }

    /** One BigQueryTable corresponds with one PACE Table. */
    inner class BigQueryTable(
        schema: Schema,
        private val bqTable: BQTable,
    ) : Table(schema, bqTable.tableId.table, bqTable.fullName()) {
        override suspend fun createBlueprint() = doCreateBlueprint(bqTable)

        override val fullName = bqTable.fullName()

        override fun fqn(): String {
            return "${schema.fqn()}.${id}"
        }
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
                        DataResourceRef.newBuilder()
                            .setPlatformFqn(table.fullName())
                            .setPaceUrn(
                                ResourceUrn.newBuilder().setPlatformFqn(table.fullName()).build()
                            )
                            .setPlatform(apiProcessingPlatform)
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
