package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import com.fasterxml.jackson.databind.ObjectMapper
import com.getstrm.pace.ManifestParser.toSql
import com.getstrm.pace.processing_platforms.addRuleSet
import com.getstrm.pace.util.toYaml
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
            val dataPolicyWithGlobals = addRuleSet(policy) { globalTransform }

            File("$dbtSampleProjectDirectory/models/example/${policy.metadata.title}_view.sql").writeText(dataPolicyWithGlobals.toSql())
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

