package com.getstrm.pace.dbt

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import java.io.File
import org.jetbrains.annotations.VisibleForTesting

class ModelWriter(private val policy: DataPolicy, private val sourceModel: DbtModel) {

    fun write() {
        // Todo: take global transforms into account
        val viewGenerator = ViewGeneratorFactory.create(policy, sourceModel)
        viewGenerator.toSelectStatement(inlineParameters = true).forEach { (target, query) ->
            val targetFilePath = targetFilePath(target)
            val file = File(targetFilePath)
            val header = ModelHeaderRenderer(sourceModel, target).render()
            file.writeText("$header\n$query\n")
            println("Generated PACE model $targetFilePath")
        }
    }

    /**
     * Returns the file path where the model should be written, relative to the dbt project root.
     * The file will be located in the same directory as the source model.
     */
    @VisibleForTesting
    fun targetFilePath(target: DataPolicy.Target): String {
        return sourceModel.originalFilePath.substringBeforeLast("/") +
            "/" +
            target.ref.resourcePathList.last().name +
            ".sql"
    }
}
