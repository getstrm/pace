package com.getstrm.pace

import com.fasterxml.jackson.databind.ObjectMapper
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

        policies.map { println(it.toYaml()) }
    }
}
