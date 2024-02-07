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
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.processing_platforms.postgres.PostgresViewGenerator
import com.getstrm.pace.service.DataPolicyValidator
import com.google.protobuf.util.JsonFormat
import com.google.rpc.BadRequest.FieldViolation

object ManifestParser {

    private val objectMapper = jacksonObjectMapper().apply { setPropertyNamingStrategy(SNAKE_CASE) }
    private val protoJsonParser = JsonFormat.parser()
    private val validator = DataPolicyValidator()

    // Todo: error handling / print violations with their models
    fun createDataPolicies(manifestJson: JsonNode): List<Pair<DataPolicy, List<FieldViolation>>> {
        val manifestObjectNode = manifestJson as ObjectNode
        val modelNodes =
            (manifestObjectNode["nodes"] as ObjectNode)
                .fields()
                .asSequence()
                .filter { (_, node) -> (node as ObjectNode)["resource_type"].asText() == "model" }
                .map { (_, node) -> objectMapper.convertValue<DbtModel>(node) }
                .toList()
        val platformType = manifestObjectNode["metadata"]["adapter_type"].asText().toPlatformType()
        return modelNodes.map {
            val policy = createDataPolicy(platformType, it)
            try {
                validator.validate(policy) {
                    skipCheckPrincipals = true
                }
                policy to emptyList()
            } catch (e: BadRequestException) {
                policy to e.badRequest.fieldViolationsList
            }
        }
    }

    fun DataPolicy.toQueries(): Map<DataPolicy.Target, String> {
        val generator =
            when (this.source.ref.platform.platformType) {
                ProcessingPlatform.PlatformType.POSTGRES ->
                    PostgresViewGenerator(this) { withRenderFormatted(true) }
                ProcessingPlatform.PlatformType.DATABRICKS -> TODO()
                ProcessingPlatform.PlatformType.SNOWFLAKE -> TODO()
                ProcessingPlatform.PlatformType.BIGQUERY -> TODO()
                ProcessingPlatform.PlatformType.SYNAPSE -> TODO()
                ProcessingPlatform.PlatformType.UNRECOGNIZED -> TODO()
                else ->
                    throw IllegalArgumentException(
                        "Unsupported platform type ${this.source.ref.platform.platformType}"
                    )
            }

        return generator.toSelectStatement()
    }

    private fun createDataPolicy(
        platformType: ProcessingPlatform.PlatformType,
        model: DbtModel
    ): DataPolicy {
        val dataPolicyBuilder = dataPolicy {
            metadata = metadata {
                title = model.name
                description = model.description
                tags.addAll(model.tags)
            }
            source = source {
                ref = resourceUrn {
                    integrationFqn = model.fqn.joinToString(".")
                    platform = processingPlatform { this.platformType = platformType }
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
        }.toBuilder()

        // FIXME this is happy flow only!
        model.meta["pace"]?.let {
            protoJsonParser.merge(it.toString().reader(), dataPolicyBuilder)

            dataPolicyBuilder.ruleSetsBuilderList.forEach { ruleSet ->
                val targetBuilder = ruleSet.targetBuilder

                targetBuilder.type = DataPolicy.Target.TargetType.DBT_SQL

                val refBuilder = targetBuilder.refBuilder

                when {
                    refBuilder.integrationFqn.isEmpty() && refBuilder.resourcePathList.isEmpty() -> {
                        refBuilder.resourcePathList += dataPolicyBuilder.source.ref.resourcePathList.mapIndexed { index, resourceNode ->
                            resourceNode {
                                name = if (index == dataPolicyBuilder.source.ref.resourcePathCount - 1) {
                                    "${resourceNode.name}_view"
                                } else {
                                    resourceNode.name
                                }
                            }
                        }

                        refBuilder.integrationFqn = "${dataPolicyBuilder.source.ref.integrationFqn}_view"
                    }
                    refBuilder.integrationFqn.isEmpty() && refBuilder.resourcePathList.isNotEmpty() -> {
                        refBuilder.integrationFqn = refBuilder.resourcePathList.joinToString(".")
                    }
                    refBuilder.integrationFqn.isNotEmpty() && refBuilder.resourcePathList.isEmpty() -> {
                        refBuilder.addAllResourcePath(refBuilder.integrationFqn.split(".").map { resourceNode { name = it } })
                    }
                }
            }
        }

        return dataPolicyBuilder.build()
    }
}


private fun String?.toPlatformType(): ProcessingPlatform.PlatformType {
    when (this) {
        "postgres" -> return ProcessingPlatform.PlatformType.POSTGRES
        else -> throw IllegalArgumentException("Unsupported dbt adapter_type $this")
    }
}
