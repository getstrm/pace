package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform.PlatformType.BIGQUERY
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
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
import com.google.cloud.datacatalog.v1.PolicyTagManagerClient
import com.google.rpc.DebugInfo
import org.slf4j.LoggerFactory

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
