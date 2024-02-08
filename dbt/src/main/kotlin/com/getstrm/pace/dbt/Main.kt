package com.getstrm.pace.dbt

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

fun main() {
    // Note! Working dir when running this must be the root of the desired dbt project!
    val manifestJson = File("target/manifest.json").readText().let {
        ObjectMapper().readTree(it)
    }

    val dataPolicies = ManifestParser.createDataPolicies(manifestJson)
    dataPolicies.forEach { (policy, sourceModel, violations) ->
        if (violations.isEmpty()) {
            ModelWriter(policy, sourceModel).write()
        } else {
            println("Skipping policy for source model ${sourceModel.originalFilePath} due to violations: $violations")
        }
    }
}
