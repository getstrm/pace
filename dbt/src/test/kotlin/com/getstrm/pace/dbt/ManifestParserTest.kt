package com.getstrm.pace.dbt

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.RuleSetKt.FilterKt.GenericFilterKt.condition as genericFilterCondition
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.Target.TargetType.DBT_SQL
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.RuleSetKt.FieldTransformKt.TransformKt.fixed
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.RuleSetKt.FieldTransformKt.TransformKt.identity
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.RuleSetKt.FieldTransformKt.transform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.RuleSetKt.FilterKt.genericFilter
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.RuleSetKt.fieldTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.RuleSetKt.filter
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.field
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.metadata
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.principal
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.ruleSet
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.source
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.target
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform.PlatformType.POSTGRES
import build.buf.gen.getstrm.pace.api.entities.v1alpha.dataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.processingPlatform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ManifestParserTest {

    @Test
    fun `model and column meta - everything enabled (implicitly)`() {
        // Given a dbt manifest with a model and column meta
        val manifest = readManifest("model_and_column_meta.json")

        // When it is transformed into a list of data policies
        val actual = ManifestParser.createDataPolicies(manifest)

        // Then
        actual.size shouldBe 1
        val dbtPolicy =
            actual.first().also {
                it.sourceModel.fqn shouldBe listOf("dbt_postgres_project", "staging", "stg_demo")
                it.violations.shouldBeEmpty()
                it.policy.ruleSetsList.size shouldBe 1
            }

        dbtPolicy.policy shouldBe dataPolicy {
            metadata = metadata {
                title = "dbt_postgres_project.staging.stg_demo"
                description = ""
            }
            source = source {
                ref = resourceUrn {
                    platform = processingPlatform { platformType = POSTGRES }
                    integrationFqn = "postgres.public.stg_demo"
                    resourcePath.addAll(listOfNodes("postgres", "public", "stg_demo"))
                }

                fields.addAll(
                    listOf(
                        field { nameParts += "transactionid" },
                        field { nameParts += "userid" },
                        field { nameParts += "email" },
                        field { nameParts += "age" },
                        field { nameParts += "transactionamount" },
                        field { nameParts += "brand" },
                    ),
                )
            }
            ruleSets += ruleSet {
                target = target {
                    type = DBT_SQL
                    ref = resourceUrn {
                        platform = processingPlatform { platformType = POSTGRES }
                        integrationFqn = "my_other_db.my_other_schema.my_custom_view_name"
                        resourcePath.addAll(
                            listOfNodes(
                                "my_other_db",
                                "my_other_schema",
                                "my_custom_view_name",
                            ),
                        )
                    }
                }
                fieldTransforms += fieldTransform {
                    field = field { nameParts += "userid" }
                    transforms += transform {
                        principals += principal { group = "fraud_and_risk" }
                        principals += principal { group = "administrator" }
                        identity = identity {}
                    }
                    transforms += transform { fixed = fixed { value = "42" } }
                }
                fieldTransforms += fieldTransform {
                    field = field { nameParts += "email" }
                    transforms += transform {
                        principals += principal { group = "administrator" }
                        identity = identity {}
                    }
                    transforms += transform { fixed = fixed { value = "'banaan@banaan.com'" } }
                }
                filters += filter {
                    genericFilter = genericFilter {
                        conditions += genericFilterCondition {
                            principals += principal { group = "administrator" }
                            principals += principal { group = "fraud_and_risk" }
                            condition = "true"
                        }
                        conditions += genericFilterCondition { condition = "age > 8" }
                    }
                }
            }
        }
    }

    @Test
    fun `column meta only - single field disabled`() {
        // Given a dbt manifest with a single field disabled
        val manifest = readManifest("column_meta_single_field_disabled.json")

        // When it is transformed into a list of data policies
        val actual = ManifestParser.createDataPolicies(manifest)

        // Then
        actual.size shouldBe 1
        val dataPolicy = actual.first().policy
        dataPolicy.ruleSetsList.size shouldBe 1

        dataPolicy.ruleSetsList.first() shouldBe ruleSet {
            target = target {
                type = DBT_SQL
                ref = resourceUrn {
                    platform = processingPlatform { platformType = POSTGRES }
                    integrationFqn = "postgres.public.stg_demo_view"
                    resourcePath.addAll(listOfNodes("postgres", "public", "stg_demo_view"))
                }
            }
            fieldTransforms += fieldTransform {
                field = field { nameParts += "email" }
                transforms += transform {
                    principals += principal { group = "administrator" }
                    identity = identity {}
                }
                transforms += transform { fixed = fixed { value = "'banaan@banaan.com'" } }
            }
        }
    }

    @Test
    fun `model and column meta - fully disabled`() {
        // Given a dbt manifest that is fully disabled
        val manifest = readManifest("model_and_column_meta_fully_disabled.json")

        // When it is transformed into a list of data policies
        val actual = ManifestParser.createDataPolicies(manifest)

        // Then
        actual.shouldBeEmpty()
    }

    @Test
    fun `model meta only - enabled (implicitly)`() {
        // Given a dbt manifest with model meta only and which is enabled implicitly
        val manifest = readManifest("model_meta_only.json")

        // When it is transformed into a list of data policies
        val actual = ManifestParser.createDataPolicies(manifest)

        // Then
        actual.size shouldBe 1
        val dataPolicy = actual.first().policy
        dataPolicy.ruleSetsList.size shouldBe 1
        dataPolicy.ruleSetsList.first() shouldBe ruleSet {
            target = target {
                type = DBT_SQL
                ref = resourceUrn {
                    platform = processingPlatform { platformType = POSTGRES }
                    integrationFqn = "postgres.public.stg_demo_view"
                    resourcePath.addAll(listOfNodes("postgres", "public", "stg_demo_view"))
                }
            }
            filters += filter {
                genericFilter = genericFilter {
                    conditions += genericFilterCondition {
                        principals += principal { group = "administrator" }
                        principals += principal { group = "fraud_and_risk" }
                        condition = "true"
                    }
                    conditions += genericFilterCondition { condition = "age > 8" }
                }
            }
        }
    }

    @Test
    fun `model meta only - disabled explicitly`() {
        // Given a dbt manifest with model meta only and which is disabled explicitly
        val manifest = readManifest("model_meta_only_disabled.json")

        // When it is transformed into a list of data policies
        val actual = ManifestParser.createDataPolicies(manifest)

        // Then
        actual.shouldBeEmpty()
    }

    @Test
    fun `no policy configuration - enabled explicitly`() {
        // Given a dbt manifest with no policy configuration but which is enabled explicitly
        val manifest = readManifest("no_config_enabled_explicitly.json")

        // When it is transformed into a list of data policies
        val actual = ManifestParser.createDataPolicies(manifest)

        // Then a policy with an "empty" rule set is generated
        actual.size shouldBe 1
        actual.first().policy shouldBe dataPolicy {
            metadata = metadata {
                title = "dbt_postgres_project.staging.stg_demo"
                description = ""
            }
            source = source {
                ref = resourceUrn {
                    platform = processingPlatform { platformType = POSTGRES }
                    integrationFqn = "postgres.public.stg_demo"
                    resourcePath.addAll(listOfNodes("postgres", "public", "stg_demo"))
                }

                fields.addAll(
                    listOf(
                        field { nameParts += "transactionid" },
                        field { nameParts += "userid" },
                        field { nameParts += "email" },
                        field { nameParts += "age" },
                        field { nameParts += "transactionamount" },
                        field { nameParts += "brand" },
                    ),
                )
            }
            ruleSets += ruleSet {
                target = target {
                    type = DBT_SQL
                    ref = resourceUrn {
                        platform = processingPlatform { platformType = POSTGRES }
                        integrationFqn = "postgres.public.stg_demo_view"
                        resourcePath.addAll(listOfNodes("postgres", "public", "stg_demo_view"))
                    }
                }
            }
        }
    }

    @Test
    fun `models generated by pace should be skipped`() {
        // Given a dbt manifest with a model generated by pace
        val manifest = readManifest("model_generated_by_pace.json")

        // When it is transformed into a list of data policies
        val actual = ManifestParser.createDataPolicies(manifest)

        // Then
        actual.shouldBeEmpty()
    }

    private fun readManifest(filename: String): JsonNode {
        return this::class.java.classLoader.getResource(filename)?.let {
            MAPPER.readTree(it.readText())
        } ?: throw IllegalArgumentException("File not found: $filename")
    }

    private fun listOfNodes(vararg nodeName: String) = nodeName.map { resourceNode { name = it } }

    companion object {
        private val MAPPER =
            jacksonObjectMapper().apply {
                setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            }
    }
}
