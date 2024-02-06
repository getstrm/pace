package com.getstrm.pace

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
    fun foo() {
        val policies = ManifestParser.createBluePrints(manifestJson)
        policies.shouldNotBeEmpty()

        policies.forEach { policy ->
            val dataPolicyWithGlobals = addRuleSet(policy, DBT_SQL) { globalTransform }
            val queries = dataPolicyWithGlobals.toQueries()

            queries.forEach { (target, query) ->
                File("$dbtSampleProjectDirectory/models/example/${target.ref.resourcePathList.last().name}.sql").writeText(query)
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
                                    FieldTransform.Transform.Fixed.newBuilder().setValue("banaan").build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
    }
}

