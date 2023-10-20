package com.getstrm.daps.service

import ProcessingPlatformConfigurationError
import ProcessingPlatformNotFoundException
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformGroupsRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformTablesRequest
import com.getstrm.daps.bigquery.BigQueryClient
import com.getstrm.daps.config.AppConfig
import com.getstrm.daps.config.ProcessingPlatformConfiguration
import com.getstrm.daps.dao.TokensDao
import com.getstrm.daps.databricks.DatabricksClient
import com.getstrm.daps.domain.Group
import com.getstrm.daps.domain.ProcessingPlatformInterface
import com.getstrm.daps.domain.Table
import com.getstrm.daps.snowflake.SnowflakeClient
import org.springframework.stereotype.Component

@Component
class ProcessingPlatformsService(
    config: ProcessingPlatformConfiguration,
    private val tokensDao: TokensDao,
) {
    final val platforms: Map<String, ProcessingPlatformInterface>

    init {
        val databricks = config.databricks.map { DatabricksClient(it) }
        val snowflake = config.snowflake.map { SnowflakeClient(it, tokensDao) }
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
        (platforms[request.platform.id] ?: throw ProcessingPlatformNotFoundException(request.platform.id)).listTables()

    suspend fun listProcessingPlatformGroups(request: ListProcessingPlatformGroupsRequest): List<Group> =
        (platforms[request.platform.id] ?: throw ProcessingPlatformNotFoundException(request.platform.id)).listGroups()

    suspend fun createBarePolicy(platform: DataPolicy.ProcessingPlatform?, tableName: String): DataPolicy {
        val processingPlatformInterface = platforms[platform!!.id] ?: throw ProcessingPlatformNotFoundException(platform.id)
        val table = processingPlatformInterface.createTable(tableName)
        return table.toDataPolicy(DataPolicy.ProcessingPlatform.newBuilder().setId(platform.id).setPlatformType(processingPlatformInterface.type).build())
    }
}
