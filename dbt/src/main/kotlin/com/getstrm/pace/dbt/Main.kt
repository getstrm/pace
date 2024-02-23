package com.getstrm.pace.dbt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val basePath = if (args.isEmpty())  {
        Paths.get("").toAbsolutePath().toString()
    } else if (args.size == 1) {
        args[0]
    } else {
        println("Either provide 0 arguments or an absolute path to the dbt project directory.")
        exitProcess(1)
    }
    val objectMapper = jacksonObjectMapper().apply { setPropertyNamingStrategy(
        PropertyNamingStrategies.SNAKE_CASE
    ) }
//    val objectNode = objectMapper.createObjectNode()
//    objectNode.put("fruit", "banaan")
//    println(objectMapper.writeValueAsString(objectNode))
//    val manifestJson =
//        File("$basePath/target/manifest.json").readText().let { ObjectMapper().readTree(it) }
//    val manifestObjectNode = manifestJson as ObjectNode
//    val modelNodes =
//        (manifestObjectNode["nodes"] as ObjectNode)
//            .fields()
//            .asSequence()
//            .filter { (_, node) -> (node as ObjectNode)["resource_type"].asText() == "model" }
//            .map { (_, node) -> {
//                objectMapper.readValue(objectMapper.writeValueAsString(node),DbtModel::class.java)
//            } }
//            .toList()
//
//    println(modelNodes)
//
//    exitProcess(0)

    System.setProperty("org.jooq.no-logo", "true")
    System.setProperty("org.jooq.no-tips", "true")

    // Note! Working dir when running this must be the root of the desired dbt project!
    try {
        val ManifestParser = ManifestParser()
        val manifestJson =
            File("$basePath/target/manifest.json").readText().let { ObjectMapper().readTree(it) }

        val dataPolicies = ManifestParser.createDataPolicies(manifestJson)
        dataPolicies.forEach { (policy, sourceModel, violations) ->
            if (violations.isEmpty()) {
                ModelWriter(policy, sourceModel, basePath).write()
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
