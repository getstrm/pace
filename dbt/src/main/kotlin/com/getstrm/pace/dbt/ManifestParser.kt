package com.getstrm.pace.dbt

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
import com.getstrm.pace.service.DataPolicyValidator
import com.google.protobuf.util.JsonFormat
import com.google.rpc.BadRequest.FieldViolation

object ManifestParser {

    private val objectMapper = jacksonObjectMapper().apply { setPropertyNamingStrategy(SNAKE_CASE) }
    private val protoJsonParser = JsonFormat.parser().ignoringUnknownFields()
    private val validator = DataPolicyValidator()

    // Todo: error handling / print violations with their models
    fun createDataPolicies(manifestJson: JsonNode): List<DbtPolicy> {
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
                validator.validate(policy) { skipCheckPrincipals = true }
                DbtPolicy(policy, it, emptyList())
            } catch (e: BadRequestException) {
                DbtPolicy(policy, it, e.badRequest.fieldViolationsList)
            }
        }
    }

    private fun createDataPolicy(
        platformType: ProcessingPlatform.PlatformType,
        model: DbtModel
    ): DataPolicy {
        val dataPolicyBuilder =
            dataPolicy {
                    metadata = metadata {
                        // Since the model FQN differs from the db/schema/table path on the
                        // underlying platform,
                        // and purely depends on DBT project id, and the directory structure used,
                        // we "store" the fqn in the title for now. I.e. the title represents the
                        // source
                        // model's fqn, and the source resource path represents the actual path on
                        // the platform.
                        title = model.fqn.joinToString(".")
                        description = model.description.orEmpty()
                        tags.addAll(model.tags)
                    }
                    source = source {
                        ref = resourceUrn {
                            platform = processingPlatform { this.platformType = platformType }
                            val resourcePathComponents =
                                listOf(model.database, model.schema, model.name)
                            integrationFqn = resourcePathComponents.joinToString(".")
                            resourcePath.addAll(
                                resourcePathComponents.map { resourceNode { name = it } }
                            )
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
                .toBuilder()

        mergePolicyFromModelMeta(model, dataPolicyBuilder)
        mergeFieldTransformsFromColumnMeta(model, dataPolicyBuilder)

        return dataPolicyBuilder.build()
    }

    private fun mergeFieldTransformsFromColumnMeta(
        model: DbtModel,
        dataPolicyBuilder: DataPolicy.Builder
    ) {
        val hasColumnTransforms =
            model.columns.any { (_, column) ->
                val transforms = column.meta["pace"]?.get("transforms")
                transforms != null && transforms.isArray && transforms.toList().isNotEmpty()
            }

        if (hasColumnTransforms) {
            // Field transforms on columns have precedence over transforms in the model meta,
            // so we clear them here, and also keep only the first rule set.
            val ruleSet =
                dataPolicyBuilder.ruleSetsBuilderList.firstOrNull()?.clearFieldTransforms()
                    ?: DataPolicy.RuleSet.newBuilder()
            ruleSet.setRuleSetTarget(dataPolicyBuilder)

            // Todo: maybe add a basic validation in case of multiple rule sets?
            dataPolicyBuilder.clearRuleSets()

            dataPolicyBuilder.addRuleSets(ruleSet)

            model.columns.forEach { (_, column) ->
                val transforms = column.meta["pace"]?.get("transforms")
                if (transforms != null && transforms.isArray && transforms.toList().isNotEmpty()) {
                    val transformsNode = objectMapper.createObjectNode()
                    transformsNode.set<ObjectNode>("transforms", transforms)

                    val fieldTransforms =
                        DataPolicy.RuleSet.FieldTransform.newBuilder().apply {
                            protoJsonParser.merge(transformsNode.toString().reader(), this)
                        }
                    fieldTransforms.field = field {
                        // We only need to specify the name here, the full details are under the
                        // source fields
                        nameParts += column.name
                    }

                    dataPolicyBuilder.ruleSetsBuilderList
                        .first()
                        .addFieldTransforms(fieldTransforms)
                }
            }
        }
    }

    private fun mergePolicyFromModelMeta(model: DbtModel, dataPolicyBuilder: DataPolicy.Builder) {
        model.meta["pace"]?.let {
            protoJsonParser.merge(it.toString().reader(), dataPolicyBuilder)

            dataPolicyBuilder.ruleSetsBuilderList.forEach { ruleSet ->
                ruleSet.setRuleSetTarget(dataPolicyBuilder)
            }
        }
    }

    private fun DataPolicy.RuleSet.Builder.setRuleSetTarget(dataPolicyBuilder: DataPolicy.Builder) {
        targetBuilder.type = DataPolicy.Target.TargetType.DBT_SQL

        val refBuilder = targetBuilder.refBuilder
        refBuilder.platform = dataPolicyBuilder.source.ref.platform

        when {
            refBuilder.integrationFqn.isEmpty() && refBuilder.resourcePathList.isEmpty() -> {
                refBuilder.addAllResourcePath(
                    dataPolicyBuilder.source.ref.resourcePathList.mapIndexed { index, resourceNode
                        ->
                        resourceNode {
                            name =
                                if (index == dataPolicyBuilder.source.ref.resourcePathCount - 1) {
                                    "${resourceNode.name}_view"
                                } else {
                                    resourceNode.name
                                }
                        }
                    },
                )

                refBuilder.integrationFqn = "${dataPolicyBuilder.source.ref.integrationFqn}_view"
            }
            refBuilder.integrationFqn.isEmpty() && refBuilder.resourcePathList.isNotEmpty() -> {
                refBuilder.integrationFqn = refBuilder.resourcePathList.joinToString(".")
            }
            refBuilder.integrationFqn.isNotEmpty() && refBuilder.resourcePathList.isEmpty() -> {
                refBuilder.addAllResourcePath(
                    refBuilder.integrationFqn.split(".").map { resourceNode { name = it } },
                )
            }
        }
    }

    private fun String?.toPlatformType(): ProcessingPlatform.PlatformType =
        when (this) {
            "postgres" -> ProcessingPlatform.PlatformType.POSTGRES
            "bigquery" -> ProcessingPlatform.PlatformType.BIGQUERY
            "databricks" -> ProcessingPlatform.PlatformType.DATABRICKS
            "snowflake" -> ProcessingPlatform.PlatformType.SNOWFLAKE
            "synapse" -> ProcessingPlatform.PlatformType.SYNAPSE
            else -> throw IllegalArgumentException("Unsupported dbt adapter_type $this")
        }
}

data class DbtPolicy(
    val policy: DataPolicy,
    val sourceModel: DbtModel,
    val violations: List<FieldViolation>,
)
