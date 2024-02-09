package com.getstrm.pace.dbt

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.getstrm.pace.processing_platforms.bigquery.BigQueryViewGenerator
import com.getstrm.pace.processing_platforms.databricks.DatabricksViewGenerator
import com.getstrm.pace.processing_platforms.postgres.PostgresViewGenerator
import com.getstrm.pace.processing_platforms.snowflake.SnowflakeViewGenerator
import com.getstrm.pace.processing_platforms.synapse.SynapseViewGenerator

object ViewGeneratorFactory {

    fun create(dataPolicy: DataPolicy, sourceModel: DbtModel): ProcessingPlatformViewGenerator {
        return when (dataPolicy.source.ref.platform.platformType) {
            ProcessingPlatform.PlatformType.POSTGRES ->
                PostgresViewGenerator(dataPolicy) { withRenderFormatted(true) }

            ProcessingPlatform.PlatformType.DATABRICKS -> DatabricksViewGenerator(dataPolicy) {
                withRenderFormatted(true)
            }

            ProcessingPlatform.PlatformType.SNOWFLAKE -> SnowflakeViewGenerator(dataPolicy) {
                withRenderFormatted(true)
            }
            ProcessingPlatform.PlatformType.BIGQUERY -> {
                val userGroupsTable = sourceModel.meta["pace_user_groups_table"]?.asText()
                require(userGroupsTable != null) {
                    "Missing required metadata 'pace_user_groups_table'. " +
                        "Please add it to your DBT project model metadata"
                }
                BigQueryViewGenerator(dataPolicy, userGroupsTable) { withRenderFormatted(true) }
            }

            ProcessingPlatform.PlatformType.SYNAPSE -> SynapseViewGenerator(dataPolicy) {
                withRenderFormatted(true)
            }
            ProcessingPlatform.PlatformType.UNRECOGNIZED -> TODO()
            else ->
                throw IllegalArgumentException(
                    "Unsupported platform type ${dataPolicy.source.ref.platform.platformType}",
                )
        }
    }
}
