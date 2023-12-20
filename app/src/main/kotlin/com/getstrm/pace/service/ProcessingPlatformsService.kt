package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.*
import com.getstrm.pace.config.ProcessingPlatformConfiguration
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.processing_platforms.bigquery.BigQueryClient
import com.getstrm.pace.processing_platforms.databricks.DatabricksClient
import com.getstrm.pace.processing_platforms.postgres.PostgresClient
import com.getstrm.pace.processing_platforms.snowflake.SnowflakeClient
import com.getstrm.pace.processing_platforms.synapse.SynapseClient
import com.getstrm.pace.util.DEFAULT_PAGE_PARAMETERS
import com.getstrm.pace.util.PagedCollection
import com.google.rpc.BadRequest
import com.google.rpc.ResourceInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Database as ApiDatabase
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema as ApiSchema

@Component
class ProcessingPlatformsService(
    config: ProcessingPlatformConfiguration,
    private val globalTransformsService: GlobalTransformsService,
    private val dataPolicyValidatorService: DataPolicyValidatorService,
) {
    final val platforms: Map<String, ProcessingPlatformClient>

    private val log = LoggerFactory.getLogger(DatabricksClient::javaClass.name)

    init {
        val databricks = config.databricks.map { DatabricksClient(it) }
        val snowflake = config.snowflake.map { SnowflakeClient(it) }
        val bigQuery = config.bigquery.map { BigQueryClient(it) }
        val postgres = config.postgres.map { PostgresClient(it) }
        val synapse = config.synapse.map { SynapseClient(it) }
        platforms = (databricks + snowflake + bigQuery + postgres + synapse).associateBy { it.id }
    }

    suspend fun listGroups(platformId: String): PagedCollection<Group> =
        platforms[platformId]?.listGroups(DEFAULT_PAGE_PARAMETERS) ?: throw processingPlatformNotFound(platformId)

    suspend fun listGroupNames(platformId: String): Set<String> =
        listGroups(platformId).map { it.name }.data.toSet()

    fun getProcessingPlatform(dataPolicy: DataPolicy): ProcessingPlatformClient {
        val processingPlatform = platforms[dataPolicy.platform.id] ?: throw processingPlatformNotFound(
            dataPolicy.platform.id,
            dataPolicy.platform.platformType.name
        )
        return processingPlatform.also {
            if (it.type != dataPolicy.platform.platformType) {
                throw BadRequestException(
                    BadRequestException.Code.INVALID_ARGUMENT,
                    BadRequest.newBuilder()
                        .addAllFieldViolations(
                            listOf(
                                BadRequest.FieldViolation.newBuilder()
                                    .setField("dataPolicy.platform.platformType")
                                    .setDescription("Platform type in DataPolicy ${dataPolicy.platform.platformType} does not correspond with configured platform ${dataPolicy.platform.id} of type ${it.type}")
                                    .build()
                            )
                        )
                        .build()
                )
            }
        }
    }

    suspend fun listProcessingPlatformTables(request: ListTablesRequest): PagedCollection<ProcessingPlatformClient.Table> {
        val processingPlatformClient: ProcessingPlatformClient =
            platforms[request.platformId] ?: throw processingPlatformNotFound(request.platformId)
        val schema: ProcessingPlatformClient.Schema = processingPlatformClient.getDatabase(request.databaseId).getSchema(request.schemaId)
        return schema.listTables(
            request.pageParameters
        )
    }

    suspend fun listProcessingPlatformGroups(request: ListGroupsRequest): PagedCollection<Group> =
        (platforms[request.platformId] ?: throw processingPlatformNotFound(request.platformId)).listGroups(request.pageParameters)


    fun getBlueprintPolicy(request: GetBlueprintPolicyRequest): GetBlueprintPolicyResponse {
        TODO("Not yet implemented")
    }

    /*
    suspend fun getBlueprintPolicy(platformId: String, tableName: String): GetBlueprintPolicyResponse {
        val processingPlatformInterface = platforms[platformId] ?: throw processingPlatformNotFound(platformId)
        val table = processingPlatformInterface.getTable(tableName)
        val baseDataPolicy = table.toDataPolicy(
            ProcessingPlatform.newBuilder().setId(platformId)
                .setPlatformType(processingPlatformInterface.type).build()
        )

        return globalTransformsService.addRuleSet(baseDataPolicy).let { blueprint ->
            val builder = GetBlueprintPolicyResponse.newBuilder().setDataPolicy(blueprint)
            try {
                dataPolicyValidatorService.validate(blueprint, listGroupNames(platformId))
                builder.build()
            } catch (e: BadRequestException) {
                e.status.description?.let {
                    builder.setViolation(
                        FieldViolation.newBuilder().setDescription(e.status.description))
                        .build()

                } ?: throw InternalException(InternalException.Code.INTERNAL, DebugInfo.newBuilder()
                    .setDetail("DataPolicyValidatorService.validate threw an exception without a description. ${BUG_REPORT}" )
                    .addAllStackEntries(e.stackTrace.map { it.toString()})
                    .build()
                )

            }
        }
    }
     */

    private fun processingPlatformNotFound(platformId: String, owner: String? = null) = ResourceException(
        ResourceException.Code.NOT_FOUND, ResourceInfo.newBuilder()
            .setResourceType(PROCESSING_PLATFORM)
            .setResourceName(platformId)
            .setDescription("Platform with id $platformId not found, please ensure it is present in the configuration of the processing platforms.")
            .apply { if (owner != null) setOwner(owner) }
            .build()
    )

    fun listDatabases(request: ListDatabasesRequest): PagedCollection<ApiDatabase> {
        TODO("Not yet implemented")
    }
    fun listSchemas(request: ListSchemasRequest): PagedCollection<ApiSchema> {
        TODO("Not yet implemented")
    }

    companion object {
        private const val PROCESSING_PLATFORM = "Processing Platform"
    }
}
