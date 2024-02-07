package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.Target.TargetType.DBT_SQL
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import com.fasterxml.jackson.databind.ObjectMapper
import com.getstrm.pace.ManifestParser.toQueries
import com.getstrm.pace.processing_platforms.addRuleSet
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File
import org.junit.jupiter.api.Test

class ManifestParserTest {

    private val manifestJson =
        File("$dbtSampleProjectDirectory/target/manifest.json").readText().let {
            ObjectMapper().readTree(it)
        }

    @Test
    fun `print models`() {
        policyAction { source, target, query ->
            println("---------------------------")
            println("Model: ${source.ref.resourcePathList.last().name} - Target: ${target.ref.resourcePathList.last().name}")
            println("---------------------------")
            println("Query: $query")
        }
    }


    @Test
    fun `write models`() {
        policyAction { source, target, query ->
            File("$dbtSampleProjectDirectory/models/example/${target.ref.resourcePathList.last().name}.sql").writeText(query)
        }
    }

    private fun policyAction(block: (DataPolicy.Source, DataPolicy.Target, String) -> Unit) {
        val blueprints = ManifestParser.createBluePrints(manifestJson).also { it.shouldNotBeEmpty() }

        blueprints.forEach {
            val dataPolicyWithGlobals = addRuleSet(it, DBT_SQL) { globalTransform }
            val queries = dataPolicyWithGlobals.toQueries()
            queries.forEach { (target, query) ->
                block(dataPolicyWithGlobals.source, target, query)
            }
        }
    }

    companion object {
        private const val dbtSampleProjectDirectory = "src/main/dbt_sample_project"

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

