package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import com.fasterxml.jackson.databind.ObjectMapper
import com.getstrm.pace.processing_platforms.addRuleSet
import com.getstrm.pace.util.toYaml
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test

class ManifestParserTest {

    private val manifestJson =
        this::class.java.getResource("/manifest.json").readText().let {
            ObjectMapper().readTree(it)
        }

    @Test
    fun foo() {
        val policies = ManifestParser.createBluePrints(manifestJson)
        policies.shouldNotBeEmpty()

        policies.forEach { policy ->
            val ς = addRuleSet(policy){ tag -> globalTransform}
            println(ς.toYaml())
            
        }
    }
}

val globalTransform = GlobalTransform.newBuilder()
    .setTagTransform(GlobalTransform.TagTransform.newBuilder()
        .setTagContent("banaan")
        .addTransforms(FieldTransform.Transform.newBuilder()
            .setFixed(FieldTransform.Transform.Fixed.newBuilder()
                .setValue("banaan")
                .build())
            .build())
        
        .build())
    .build()
