package com.getstrm.pace.dbt

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.Target.TargetType.DBT_SQL
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import com.fasterxml.jackson.databind.ObjectMapper
import com.getstrm.pace.processing_platforms.addRuleSet
import com.google.rpc.BadRequest.FieldViolation
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("Disabled for GH CI")
class ManifestParserTest {

    private val manifestJson =
        File("$DBT_BIGQUERY_PROJECT_DIRECTORY/target/manifest.json").readText().let {
            ObjectMapper().readTree(it)
        }

    @Test
    fun `print models`() {
        policyAction { source, target, query, violations ->
            println("---------------------------")
            println("Model: ${source.ref.resourcePathList.last().name} - Target: ${target.ref.resourcePathList.last().name}")
            println("---------------------------")
            if (violations.isNotEmpty()) {
                println("Violations: $violations")
            } else {
                println("Query: $query")
            }
        }
    }

    private fun policyAction(block: (DataPolicy.Source, DataPolicy.Target, String, List<FieldViolation>) -> Unit) {
        val blueprints =
            ManifestParser.createDataPolicies(manifestJson).also { it.shouldNotBeEmpty() }

        blueprints.forEach { (policy, sourceModel, violations) ->
            val dataPolicyWithGlobals = addRuleSet(policy, DBT_SQL) { globalTransform }
            val queries = ViewGeneratorFactory.create(dataPolicyWithGlobals, sourceModel)
                .toSelectStatement(inlineParameters = true)
            queries.forEach { (target, query) ->
                block(dataPolicyWithGlobals.source, target, query, violations)
            }
        }
    }

    companion object {
        private const val DBT_POSTGRES_PROJECT_DIRECTORY = "src/main/dbt_postgres_project"
        private const val DBT_BIGQUERY_PROJECT_DIRECTORY = "src/main/dbt_bigquery_project"

        private val globalTransform =
            GlobalTransform.newBuilder()
                .setTagTransform(
                    GlobalTransform.TagTransform.newBuilder()
                        .setTagContent("banaan")
                        .addTransforms(
                            FieldTransform.Transform.newBuilder()
                                .setFixed(
                                    FieldTransform.Transform.Fixed.newBuilder().setValue("'banaan'").build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
    }
}

