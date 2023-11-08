package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.config.ProcessingPlatformConfiguration
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatform
import com.getstrm.pace.processing_platforms.Table
import com.getstrm.pace.processing_platforms.bigquery.BigQueryClient
import com.getstrm.pace.processing_platforms.databricks.DatabricksClient
import com.getstrm.pace.processing_platforms.postgres.PostgresClient
import com.getstrm.pace.processing_platforms.snowflake.SnowflakeClient
import com.google.rpc.BadRequest
import com.google.rpc.ResourceInfo
import org.springframework.stereotype.Component

@Component
class ProcessingPlatformsService(
    config: ProcessingPlatformConfiguration,
    private val globalTransformsService: GlobalTransformsService,
    private val dataPolicyValidatorService: DataPolicyValidatorService,
) {
    final val platforms: Map<String, ProcessingPlatform>

    init {
        val databricks = config.databricks.map { DatabricksClient(it) }
        val snowflake = config.snowflake.map { SnowflakeClient(it) }
        val bigQuery = config.bigquery.map { BigQueryClient(it) }
        val postgres = config.postgres.map { PostgresClient(it) }
        platforms = (databricks + snowflake + bigQuery + postgres).associateBy { it.id }
    }

    suspend fun listGroups(platformId: String): List<Group> =
        platforms[platformId]?.listGroups() ?: throw processingPlatformNotFound(platformId)

    suspend fun listGroupNames(platformId: String): Set<String> =
        listGroups(platformId).map { it.name }.toSet()

    fun getProcessingPlatform(dataPolicy: DataPolicy): ProcessingPlatform {
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

    suspend fun listProcessingPlatformTables(platformId: String): List<Table> =
        (platforms[platformId] ?: throw processingPlatformNotFound(platformId)).listTables()

    suspend fun listProcessingPlatformGroups(platformId: String): List<Group> =
        (platforms[platformId] ?: throw processingPlatformNotFound(platformId)).listGroups()

    suspend fun getBarePolicy(platformId: String, tableName: String): DataPolicy {
        val processingPlatformInterface = platforms[platformId] ?: throw processingPlatformNotFound(platformId)
        val table = processingPlatformInterface.getTable(tableName)
        val baseDataPolicy = table.toDataPolicy(
            DataPolicy.ProcessingPlatform.newBuilder().setId(platformId)
                .setPlatformType(processingPlatformInterface.type).build()
        )

        return globalTransformsService.addRuleSet(baseDataPolicy).also {
            dataPolicyValidatorService.validate(it, listGroupNames(platformId))
        }
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
