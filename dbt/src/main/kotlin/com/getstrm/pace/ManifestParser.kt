package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.field
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.metadata
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.source
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.dataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.processingPlatform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.getstrm.pace.processing_platforms.postgres.PostgresViewGenerator
import org.jooq.Query

object ManifestParser {

    private val objectMapper = jacksonObjectMapper().apply { setPropertyNamingStrategy(SNAKE_CASE) }

    fun createBluePrints(manifestJson: JsonNode): List<DataPolicy> {
        val manifestObjectNode = manifestJson as ObjectNode
        val modelNodes =
            (manifestObjectNode["nodes"] as ObjectNode)
                .fields()
                .asSequence()
                .filter { (key, node) -> val resourceType = (node as ObjectNode)["resource_type"].asText()

                    // FIXME For the demo.csv, which is a seed based "model", we include it here as well, but the JSON schemas are different, so this could lead to errors. We should probably add the demo as an explicit model
                    resourceType == "model" || resourceType == "seed" }
                .map { (key, node) -> objectMapper.convertValue<DbtModel>(node) }
                .toList()
        val platformType = manifestObjectNode["metadata"]["adapter_type"].asText().toPlatformType()
        return modelNodes.map { createDataPolicy(platformType, it) }
    }

    fun DataPolicy.toQueries(): Map<DataPolicy.Target, String> {
        val generator = when(this.source.ref.platform.platformType) {
            ProcessingPlatform.PlatformType.POSTGRES -> PostgresViewGenerator(this) { withRenderFormatted(true) }
            ProcessingPlatform.PlatformType.DATABRICKS -> TODO()
            ProcessingPlatform.PlatformType.SNOWFLAKE -> TODO()
            ProcessingPlatform.PlatformType.BIGQUERY -> TODO()
            ProcessingPlatform.PlatformType.SYNAPSE -> TODO()
            ProcessingPlatform.PlatformType.UNRECOGNIZED -> TODO()
            else -> throw IllegalArgumentException("Unsupported platform type ${this.source.ref.platform.platformType}")
        }

        return generator.toSelectStatement()
    }

    private fun createDataPolicy(platformType: ProcessingPlatform.PlatformType, model: DbtModel): DataPolicy {
        return dataPolicy {
            metadata = metadata {
                title = model.name
                description = model.description
                tags.addAll(model.tags)
            }
            source = source {
                ref = resourceUrn {
                    integrationFqn = model.fqn.joinToString(".")
                    platform = processingPlatform {
                        this.platformType = platformType
                    }
                    resourcePath.addAll(model.fqn.map { resourceNode { name = it } })
                }
                fields.addAll(
                    model.columns.values.map { column ->
                        field {
                            // TODO doesn't handle nested?
                            nameParts += column.name
                            tags += column.tags
                            // TODO reuse sqlDataType
                            column.dataType?.let { type = it }
                        }
                    }
                )
            }
        }
    }
}

private fun String?.toPlatformType(): ProcessingPlatform.PlatformType {
    when (this) {
        "postgres" -> return ProcessingPlatform.PlatformType.POSTGRES
        else -> throw IllegalArgumentException("Unsupported dbt adapter_type $this")
    }
}
