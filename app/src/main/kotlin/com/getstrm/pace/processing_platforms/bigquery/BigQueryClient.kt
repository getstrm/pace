package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform.PlatformType.BIGQUERY
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.config.BigQueryConfig
import com.getstrm.pace.config.PPConfig
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.THOUSAND_RECORDS
import com.getstrm.pace.util.applyPageParameters
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.toTimestamp
import com.getstrm.pace.util.withPageInfo
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.*
import com.google.cloud.datacatalog.v1.PolicyTagManagerClient
import com.google.rpc.DebugInfo
import com.google.rpc.ResourceInfo
import org.slf4j.LoggerFactory
import com.google.cloud.bigquery.Table as BQTable

class BigQueryClient(
    config: PPConfig,
    serviceAccountKeyJson: String,
    private val projectId: String,
    private val userGroupsTable: String,
) : ProcessingPlatformClient(config) {
    constructor(config: BigQueryConfig) : this(
        config,
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

    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<Database> {
        
        // FIXME pagedCalls function needs to understand page tokens
        // for now just getting all of the datasets
        // how slow is this?
        val datasets = ArrayList<Dataset>()
        do {
            val page = bigQuery.listDatasets()
            datasets += page.iterateAll()
        } while(page.hasNextPage())
        val f = datasets.map{
            BigQueryDatabase(this, it)
        }
        return f.withPageInfo()
    }

    override suspend fun listSchemas(databaseId: String, pageParameters: PageParameters): PagedCollection<Schema> {
        return getDatabase(databaseId).listSchemas(pageParameters)
    }

    override suspend fun listTables(
        databaseId: String,
        schemaId: String,
        pageParameters: PageParameters
    ): PagedCollection<Table> {
        TODO("Not yet implemented")
    }

    override suspend fun getTable(databaseId: String, schemaId: String, tableId: String): Table {
        TODO("Not yet implemented")
    }

    private suspend fun getDatabase(databaseId: String): Database {
        return bigQuery.getDataset(databaseId)?.let { BigQueryDatabase(this, it) }
            ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Gcloud dataset")
                    .setResourceName(databaseId)
                    .build()
            )
    }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val viewGenerator = BigQueryViewGenerator(dataPolicy, userGroupsTable)
        val query = viewGenerator.toDynamicViewSQL().sql
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

    override suspend fun listGroups(pageParameters: PageParameters): PagedCollection<Group> {
        val query = """
            SELECT
              DISTINCT userGroup
            FROM
              $userGroupsTable
            ORDER BY userGroup
            LIMIT ${pageParameters.pageSize}
            OFFSET ${pageParameters.skip}
        """.trimIndent()
        val queryConfig = QueryJobConfiguration.newBuilder(query)
            .setUseLegacySql(false)
            .build()
        return bigQuery.query(queryConfig).iterateAll().map {
            val groupName = it.get("userGroup").stringValue
            Group(id = groupName, name = groupName)
        }.withPageInfo()
    }

    companion object {
        private fun String.toTableId(): TableId {
            val (project, dataset, table) = replace("`", "").split(".")
            return TableId.of(project, dataset, table)
        }
    }
    
    inner class BigQueryDatabase(pp: ProcessingPlatformClient, val dataset: Dataset):
        Database(
            pp = pp,
            id = dataset.generatedId,
            dbType = BIGQUERY.name,
            displayName = dataset.datasetId.dataset
        ) {
        val schema = BigQuerySchema(this)
        override suspend fun listSchemas(pageParameters: PageParameters): PagedCollection<Schema> {
            return listOf(schema).withPageInfo()
        }

        override suspend fun getSchema(schemaId: String): Schema {
            return schema
        }
    }

    inner class BigQuerySchema(database: BigQueryDatabase): Schema( database, "schema", "schema" ) {
        val dataset = database.dataset
        override suspend fun listTables(pageParameters: PageParameters): PagedCollection<Table> {
            val tables = bigQuery.listTables(dataset.datasetId).iterateAll().toList()
                .applyPageParameters(pageParameters)
                .map { table ->
                    BigQueryTable(this, table, table.generatedId)
                }
            return tables.withPageInfo()
        }

        override suspend fun getTable(tableId: String): Table {
            return listTables(THOUSAND_RECORDS).find { it.id == tableId }
                ?: throw ResourceException(
                    ResourceException.Code.NOT_FOUND,
                    ResourceInfo.newBuilder()
                        .setResourceType("Gcloud table")
                        .setResourceName(tableId)
                        .build()
                )
        }
    }

    inner class BigQueryTable(
        schema: Schema,
        private val bqtable: BQTable,
        id: String,
    ) : Table( schema, id, id ) {


        override suspend fun createBlueprint(): DataPolicy {
            // The reload ensures all metadata is fetched, including the schema
            val table = bqtable.reload()
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

        override val fullName: String
            get() = bqtable.generatedId

    }
}
