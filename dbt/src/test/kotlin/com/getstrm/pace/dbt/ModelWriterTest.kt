package com.getstrm.pace.dbt

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicyKt.target
import build.buf.gen.getstrm.pace.api.entities.v1alpha.dataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ModelWriterTest {

    @Test
    fun `file path for model in models dir root`() {
        // Given
        val sourceModel = DbtModel(
            database = "my_db",
            schema = "my_schema",
            name = "my_model",
            originalFilePath = "models/my_model.sql",
        )
        val target = target {
            ref = resourceUrn {
                resourcePath.addAll(
                    listOf(
                        "my_db",
                        "my_schema",
                        "my_model_public_view",
                    ).map { resourceNode { name = it } },
                )
            }
        }
        val writer = ModelWriter(dataPolicy {}, sourceModel)

        // When
        val filePath = writer.targetFilePath(target)

        // Then
        filePath shouldBe "models/my_model_public_view.sql"
    }

    @Test
    fun `file path for model in a different nested directory`() {
        // Given
        val sourceModel = DbtModel(
            database = "my_db",
            schema = "my_schema",
            name = "my_model",
            originalFilePath = "other_models/foo/bar/my_model.sql",
        )
        val target = target {
            ref = resourceUrn {
                resourcePath.addAll(
                    listOf(
                        "my_db",
                        "my_schema",
                        "my_model_public_view",
                    ).map { resourceNode { name = it } },
                )
            }
        }
        val writer = ModelWriter(dataPolicy {}, sourceModel)

        // When
        val filePath = writer.targetFilePath(target)

        // Then
        filePath shouldBe "other_models/foo/bar/my_model_public_view.sql"
    }
}
