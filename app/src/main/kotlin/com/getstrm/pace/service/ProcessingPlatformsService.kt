package com.getstrm.pace.service

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformGroupsRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformTablesRequest
import com.getstrm.pace.bigquery.BigQueryClient
import com.getstrm.pace.config.ProcessingPlatformConfiguration
import com.getstrm.pace.databricks.DatabricksClient
import com.getstrm.pace.domain.*
import com.getstrm.pace.snowflake.SnowflakeClient
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
        platforms[platformId]?.listGroups() ?: throw ProcessingPlatformNotFoundException(platformId)

    fun getProcessingPlatform(dataPolicy: DataPolicy): ProcessingPlatformInterface =
        (platforms[dataPolicy.platform.id] ?: throw ProcessingPlatformNotFoundException(dataPolicy.platform.id)).also {
            if (it.type != dataPolicy.platform.platformType) {
                throw ProcessingPlatformConfigurationError(
                    """Platform type in DataPolicy ${dataPolicy.platform.platformType} does not 
                        |correspond with configured platform ${dataPolicy.platform.id} of type ${it.type}
                        |
                    """.trimMargin(),
                )
            }
        }

    suspend fun listProcessingPlatformTables(request: ListProcessingPlatformTablesRequest): List<Table> =
        (platforms[request.platformId] ?: throw ProcessingPlatformNotFoundException(request.platformId)).listTables()

    suspend fun listProcessingPlatformGroups(request: ListProcessingPlatformGroupsRequest): List<Group> =
        (platforms[request.platformId] ?: throw ProcessingPlatformNotFoundException(request.platformId)).listGroups()

    suspend fun createBarePolicy(platformId: String, tableName: String): DataPolicy {
        val processingPlatformInterface =
            platforms[platformId] ?: throw ProcessingPlatformNotFoundException(platformId)
        val table = processingPlatformInterface.createTable(tableName)
        return table.toDataPolicy(
            DataPolicy.ProcessingPlatform.newBuilder().setId(platformId)
                .setPlatformType(processingPlatformInterface.type).build()
        )
    }
}
