package com.getstrm.pace.service

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformGroupsRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformTablesRequest
import com.getstrm.pace.bigquery.BigQueryClient
import com.getstrm.pace.config.ProcessingPlatformConfiguration
import com.getstrm.pace.databricks.DatabricksClient
import com.getstrm.pace.domain.Group
import com.getstrm.pace.domain.ProcessingPlatformInterface
import com.getstrm.pace.domain.Table
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.snowflake.SnowflakeClient
import com.google.rpc.BadRequest
import com.google.rpc.ResourceInfo
import org.springframework.stereotype.Component

@Component
class ProcessingPlatformsService(
    config: ProcessingPlatformConfiguration,
) {
    final val platforms: Map<String, ProcessingPlatformInterface>

    init {
        val databricks = config.databricks.map { DatabricksClient(it) }
        val snowflake = config.snowflake.map { SnowflakeClient(it) }
        val bigQuery = config.bigquery.map { BigQueryClient(it) }
        platforms = (databricks + snowflake + bigQuery).associateBy { it.id }
    }

    suspend fun listGroups(platformId: String): List<Group> =
        platforms[platformId]?.listGroups() ?: throw processingPlatformNotFound(platformId)

    fun getProcessingPlatform(dataPolicy: DataPolicy): ProcessingPlatformInterface {
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

    suspend fun listProcessingPlatformTables(request: ListProcessingPlatformTablesRequest): List<Table> =
        (platforms[request.platform.id] ?: throw processingPlatformNotFound(request.platform.id)).listTables()

    suspend fun listProcessingPlatformGroups(request: ListProcessingPlatformGroupsRequest): List<Group> =
        (platforms[request.platform.id] ?: throw processingPlatformNotFound(request.platform.id)).listGroups()

    suspend fun createBarePolicy(platform: DataPolicy.ProcessingPlatform?, tableName: String): DataPolicy {
        val processingPlatformInterface =
            platforms[platform!!.id] ?: throw processingPlatformNotFound(platform.id)
        val table = processingPlatformInterface.getTable(tableName)
        return table.toDataPolicy(
            DataPolicy.ProcessingPlatform.newBuilder().setId(platform.id)
                .setPlatformType(processingPlatformInterface.type).build()
        )
    }

    private fun processingPlatformNotFound(platformId: String, owner: String? = null) = ResourceException(
        ResourceException.Code.NOT_FOUND, ResourceInfo.newBuilder()
            .setResourceType(PROCESSING_PLATFORM)
            .setResourceName(platformId)
            .setDescription("Platform with id $platformId not found, please ensure it is present in the configuration of the processing platforms.")
            .apply { if (owner != null) setOwner(owner) }
            .build()
    )

    companion object {
        private const val PROCESSING_PLATFORM = "Processing Platform"
    }
}
