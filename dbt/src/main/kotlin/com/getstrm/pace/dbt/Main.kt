package com.getstrm.pace.dbt

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.FileNotFoundException
import kotlin.system.exitProcess

fun main() {
    System.setProperty("org.jooq.no-logo", "true")
    System.setProperty("org.jooq.no-tips", "true")

    // Note! Working dir when running this must be the root of the desired dbt project!
    try {
        val manifestJson =
            File("target/manifest.json").readText().let { ObjectMapper().readTree(it) }

        val dataPolicies = ManifestParser.createDataPolicies(manifestJson)
        dataPolicies.forEach { (policy, sourceModel, violations) ->
            if (violations.isEmpty()) {
                ModelWriter(policy, sourceModel).write()
            } else {
                println(
                    "Skipping policy for source model ${sourceModel.originalFilePath} due to violations: $violations"
                )
            }
        }
    } catch (e: FileNotFoundException) {
        println(
            "No manifest.json found in target directory. Make sure you have run dbt compile first, and are working from the dbt project's root dir."
        )
        exitProcess(1)
    }
}
