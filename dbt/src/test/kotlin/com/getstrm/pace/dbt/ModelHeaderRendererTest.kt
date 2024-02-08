package com.getstrm.pace.dbt

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.ruleSet
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.source
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.target
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.dataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.processingPlatform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ModelHeaderRendererTest {

    private val minimalDbtModel = DbtModel(
        database = "my_db",
        schema = "my_schema",
        name = "my_model",
        description = "My description.",
        columns = emptyMap(),
        tags = emptyList(),
        meta = jacksonObjectMapper().createObjectNode(),
        fqn = emptyList(),
    )

    @Test
    fun `basic policy with same db and schema as source`() {
        // Given
        val policy = dataPolicy {
            ruleSets += ruleSet {
                target = target {
                    ref = resourceUrn {
                        resourcePath.addAll(
                            listOf(
                                "my_db",
                                "my_schema",
                                "my_model",
                            ).map { resourceNode { name = it } },
                        )
                    }
                }
            }
        }

        // When
        val header = ModelHeaderRenderer(policy, minimalDbtModel).render()

        // Then
        header shouldBe """
            {#
            This file was auto-generated by PACE. Do not edit this file directly.
            #}
            {{ config(
                 materialized='view'
               )
            }}
        
        """.trimIndent()
    }

    @Test
    fun `when the target db or schema differ from the source, they must be specified in the config`() {
        // Given
        val policy = dataPolicy {
            ruleSets += ruleSet {
                target = target {
                    ref = resourceUrn {
                        resourcePath.addAll(
                            listOf(
                                "my_other_db",
                                "my_other_schema",
                                "my_model",
                            ).map { resourceNode { name = it } },
                        )
                    }
                }
            }
        }

        // When
        val header = ModelHeaderRenderer(policy, minimalDbtModel).render()

        // Then
        header shouldBe """
            {#
            This file was auto-generated by PACE. Do not edit this file directly.
            #}
            {{ config(
                 materialized='view',
                 database='my_other_db',
                 schema='my_other_schema'
               )
            }}
        
        """.trimIndent()
    }

    @Test
    fun `when the target platform is BigQuery, the view should be authorized to read from the source dataset`() {
        // Given
        val policy = dataPolicy {
            source = source {
                ref = resourceUrn {
                    platform = processingPlatform {
                        platformType = ProcessingPlatform.PlatformType.BIGQUERY
                    }
                }
            }
            ruleSets += ruleSet {
                target = target {
                    ref = resourceUrn {
                        resourcePath.addAll(
                            listOf(
                                "my_db",
                                "my_schema",
                                "my_model",
                            ).map { resourceNode { name = it } },
                        )
                    }
                }
            }
        }

        // When
        val header = ModelHeaderRenderer(policy, minimalDbtModel).render()

        // Then
        header shouldBe """
            {#
            This file was auto-generated by PACE. Do not edit this file directly.
            #}
            {{ config(
                 materialized='view',
                 grant_access_to=[
                   {'project': 'my_db', 'dataset': 'my_schema'}
                 ]
               )
            }}
        
        """.trimIndent()
    }
}
