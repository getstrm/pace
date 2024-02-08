package com.getstrm.pace.dbt

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.getstrm.pace.processing_platforms.bigquery.BigQueryViewGenerator
import com.getstrm.pace.processing_platforms.postgres.PostgresViewGenerator

object ViewGeneratorFactory {

    fun create(dataPolicy: DataPolicy): ProcessingPlatformViewGenerator {
        return when (dataPolicy.source.ref.platform.platformType) {
            ProcessingPlatform.PlatformType.POSTGRES ->
                PostgresViewGenerator(dataPolicy) { withRenderFormatted(true) }

            ProcessingPlatform.PlatformType.DATABRICKS -> TODO()
            ProcessingPlatform.PlatformType.SNOWFLAKE -> TODO()
            ProcessingPlatform.PlatformType.BIGQUERY -> {
                // Todo: should be based on a property from the profile or similar
                val userGroupsTable = "stream-machine-development.user_groups.user_groups"
                BigQueryViewGenerator(dataPolicy, userGroupsTable) { withRenderFormatted(true) }
            }

            ProcessingPlatform.PlatformType.SYNAPSE -> TODO()
            ProcessingPlatform.PlatformType.UNRECOGNIZED -> TODO()
            else ->
                throw IllegalArgumentException(
                    "Unsupported platform type ${dataPolicy.source.ref.platform.platformType}",
                )
        }
    }
}
