package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.LineageSummary
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import com.getstrm.pace.config.ProcessingPlatformConfiguration
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.throwNotFound
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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProcessingPlatformsService(
    config: ProcessingPlatformConfiguration,
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
        platforms[platformId]?.listGroups(DEFAULT_PAGE_PARAMETERS)
            ?: throwNotFound(
                platformId,
                PROCESSING_PLATFORM,
                "Platform with id $platformId not found, please ensure it is present in the configuration of the processing platforms."
            )

    suspend fun listGroupNames(platformId: String): Set<String> =
        listGroups(platformId).map { it.name }.data.toSet()

    fun getProcessingPlatform(dataPolicy: DataPolicy): ProcessingPlatformClient {
        val processingPlatform =
            platforms[dataPolicy.source.ref.platform.id]
                ?: throwNotFound(
                    dataPolicy.source.ref.platform.id,
                    PROCESSING_PLATFORM, "Platform with id ${dataPolicy.source.ref.platform.id} not found, please ensure it is present in the configuration of the processing platforms.",
                    dataPolicy.source.ref.platform.platformType.name
                )
        return processingPlatform.also {
            if (it.type != dataPolicy.source.ref.platform.platformType) {
                throw BadRequestException(
                    BadRequestException.Code.INVALID_ARGUMENT,
                    BadRequest.newBuilder()
                        .addAllFieldViolations(
                            listOf(
                                BadRequest.FieldViolation.newBuilder()
                                    .setField("dataPolicy.platform.platformType")
                                    .setDescription(
                                        "Platform type in DataPolicy ${dataPolicy.source.ref.platform.platformType} does not correspond with configured platform ${dataPolicy.source.ref.platform.id} of type ${it.type}"
                                    )
                                    .build()
                            )
                        )
                        .build()
                )
            }
        }
    }

    suspend fun getLineage(request: GetLineageRequest): LineageSummary {
        val platformClient =
            (platforms[request.platformId] ?: throwNotFound(
                request.platformId,
                PROCESSING_PLATFORM,
                "Platform with id ${request.platformId} not found, please ensure it is present in the configuration of the processing platforms."
            ))
        return platformClient.getLineage(request)
    }

    companion object {
        private const val PROCESSING_PLATFORM = "Processing Platform"
    }
}
