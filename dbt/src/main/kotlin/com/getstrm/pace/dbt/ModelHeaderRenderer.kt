package com.getstrm.pace.dbt

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform

/** Renders Jinja "headers" for a PACE DBT models, based on specific targets. */
class ModelHeaderRenderer(
    private val sourceModel: DbtModel,
    private val target: DataPolicy.Target
) {
    private val config = Config()
    private val header = StringBuilder()

    companion object {
        private const val AUTO_GENERATED_WARNING =
            """{#
    This file was auto-generated by PACE. Do not edit this file directly.
#}
"""
    }

    fun render(): String {
        header.append(AUTO_GENERATED_WARNING)
        config.add("materialized='view'")
        configureDatabaseAndSchema()
        authorizeBigQueryView()

        // Finalize
        header.append(config.render())
        header.append("\n")
        return header.toString()
    }

    private fun configureDatabaseAndSchema() {
        val sourceResourcePathList =
            listOf(sourceModel.database, sourceModel.schema, sourceModel.name)
        val targetResourcePathList = target.ref.resourcePathList
        check(targetResourcePathList.size == 3) {
            "Expected target to have 3 resource path elements, but got " +
                "${targetResourcePathList.map { it.name }}."
        }
        if (targetResourcePathList.first().name != sourceResourcePathList.first()) {
            config.add("database='${targetResourcePathList.first().name}'")
        }
        if (targetResourcePathList[1].name != sourceResourcePathList[1]) {
            config.add("schema='${targetResourcePathList[1].name}'")
        }
    }

    private fun authorizeBigQueryView() {
        // We only need to authorize the view if its dataset is not the same as the source's.
        if (
            target.ref.platform.platformType == ProcessingPlatform.PlatformType.BIGQUERY &&
                (target.ref.resourcePathList[0].name != sourceModel.database ||
                    target.ref.resourcePathList[1].name != sourceModel.schema)
        ) {
            config.add(
                """grant_access_to=[
        {'project': '${sourceModel.database}', 'dataset': '${sourceModel.schema}'}
      ]""",
            )
        }
    }

    private class Config(private val configs: MutableList<String> = mutableListOf()) :
        MutableList<String> by configs {
        fun render(): String {
            return "{{\n    config(\n${
                configs.joinToString(
                    prefix = "      ",
                    separator = ",\n      ",
                )
            }\n    )\n}}"
        }
    }
}
