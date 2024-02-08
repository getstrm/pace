package com.getstrm.pace.dbt

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.getstrm.pace.processing_platforms.bigquery.BigQueryViewGenerator
import com.getstrm.pace.processing_platforms.postgres.PostgresViewGenerator

object ViewGeneratorFactory {

    fun create(dataPolicy: DataPolicy, sourceModel: DbtModel): ProcessingPlatformViewGenerator {
        return when (dataPolicy.source.ref.platform.platformType) {
            ProcessingPlatform.PlatformType.POSTGRES ->
                PostgresViewGenerator(dataPolicy) { withRenderFormatted(true) }

            ProcessingPlatform.PlatformType.DATABRICKS -> TODO()
            ProcessingPlatform.PlatformType.SNOWFLAKE -> TODO()
            ProcessingPlatform.PlatformType.BIGQUERY -> {
                val userGroupsTable = sourceModel.meta["pace_user_groups_table"]?.asText()
                require(userGroupsTable != null) {
                    "Missing required metadata 'pace_user_groups_table'. " +
                        "Please add it to your DBT project model metadata"
                }
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
