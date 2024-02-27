package com.getstrm.pace.dbt

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import java.io.File
import org.jetbrains.annotations.VisibleForTesting

class ModelWriter(private val policy: DataPolicy, private val sourceModel: DbtModel, private val basePath: String) {

    fun write() {
        // Todo: take global transforms into account
        val viewGenerator = ViewGeneratorFactory.create(policy, sourceModel)
        viewGenerator.toSelectStatement(inlineParameters = true).forEach { (target, query) ->
            val targetFilePath = "$basePath/${targetFilePath(target)}"

            try {
                val file = File(targetFilePath)
                val header = ModelHeaderRenderer(sourceModel, target).render()
                file.writeText("$header\n$query\n")
                println("Generated PACE model $targetFilePath")
            } catch (e: Exception) {
                println("Error writing model to $targetFilePath: $e")
            }
        }
    }

    /**
     * Returns the file path where the model should be written, relative to the dbt project root.
     * The file will be located in the same directory as the source model.
     */
    @VisibleForTesting
    fun targetFilePath(target: DataPolicy.Target): String {
        return sourceModel.originalFilePath
            // Windows uses backslashes, so we need to replace them with forward slashes
            .replace("\\", "/")
            .substringBeforeLast("/") +
            "/" +
            target.ref.resourcePathList.last().name +
            ".sql"
    }
}
