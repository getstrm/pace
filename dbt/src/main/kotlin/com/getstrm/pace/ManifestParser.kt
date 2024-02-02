package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.metadata
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.source
import build.buf.gen.getstrm.pace.api.entities.v1alpha.dataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.processingPlatform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object ManifestParser {

    private val objectMapper = jacksonObjectMapper()
    private val dummyPlatform = processingPlatform {}

    fun createBluePrints(manifestJson: JsonNode): List<DataPolicy> {
        val modelNodes =
            ((manifestJson as ObjectNode)["nodes"] as ObjectNode)
                .fields()
                .asSequence()
                .filter { (key, node) -> (node as ObjectNode)["resource_type"].asText() == "model" }
                .map { (key, node) -> objectMapper.convertValue<DbtModel>(node) }
                .toList()
        return modelNodes.map { createDataPolicy(it) }
    }

    private fun createDataPolicy(model: DbtModel): DataPolicy {
        return dataPolicy {
            metadata = metadata {
                title = model.name
                description = model.description
                tags.addAll(model.tags)
            }
            source = source {
                ref = resourceUrn {
                    platform = dummyPlatform
                    resourcePath.addAll(
                        listOf(
                            resourceNode { name = model.database },
                            resourceNode { name = model.schema },
                            resourceNode {
                                name = model.name
                                isLeaf = true
                            },
                        ),
                    )
                }
                fields.addAll( model.columns.values.map{ column ->
                    DataPolicy.Field.newBuilder()
                        // TODO doesn't handle nested?
                        .addNameParts(column.name)
                        .addAllTags(column.tags)
                        // TODO reuse sqlDataType
                        .apply { column.dataType?.let{ this.setType(it)} }
                        .build()
                })
            }
        }
    }
}
