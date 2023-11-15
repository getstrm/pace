package com.getstrm.pace.dao

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.TagTransform
import com.getstrm.jooq.generated.tables.records.GlobalTransformsRecord
import com.getstrm.pace.AbstractDatabaseTest
import com.getstrm.pace.util.*
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlobalTransformsDaoTest : AbstractDatabaseTest() {
    private val underTest = GlobalTransformsDao(jooq)

    @BeforeEach
    fun setupDatabase() {
        dataSource.executeMigrations("database/global-transforms")
    }

    @Test
    fun `get transform - exists`() {
        // Given some transforms in the database
        val refAndType = GlobalTransform.RefAndType.newBuilder()
            .setRef("email")
            .setType(GlobalTransform.TransformCase.TAG_TRANSFORM.name)
            .build()

        // When
        val actual = underTest.getTransform(refAndType)

        // Then
        actual.shouldNotBeNull()

        GlobalTransform.newBuilder().merge(actual.transform!!).build() shouldBe emailTransform
    }

    @Test
    fun `get transform - does not exist`() {
        // Given a transform that does not exist
        val refAndType = GlobalTransform.RefAndType.newBuilder()
            .setRef("does-not-exist")
            .setType(GlobalTransform.TransformCase.TAG_TRANSFORM.name)
            .build()

        // When
        val actual = underTest.getTransform(refAndType)

        // Then
        actual.shouldBeNull()
    }

    @Test
    fun `list transforms`() {
        // Given a transform type
        val transformType = GlobalTransform.TransformCase.TAG_TRANSFORM

        // When all transforms are listed
        val actual = underTest.listTransforms(transformType)

        // Then
        actual shouldHaveSize 2
       actual.withoutTimestamps() shouldContainExactlyInAnyOrder listOf(emailTransform, nameTransform).toRecords()
    }

    @Test
    fun `upsert transform - update existing`() {
        // Given an update for an existing transform
        val updateToEmailTransform = emailTransform.toBuilder().apply {
            tagTransform = tagTransform.toBuilder().apply {
                addAllTransforms(transformsList.map {
                    it.toBuilder().setNullify(DataPolicy.RuleSet.FieldTransform.Transform.Nullify.getDefaultInstance())
                        .build()
                })
            }.build()
        }.build()

        // When
        val actual = underTest.upsertTransform(updateToEmailTransform)

        // Then
        actual.shouldNotBeNull()
        actual.withoutTimestamps() shouldBe updateToEmailTransform.toRecord()
    }

    @Test
    fun `upsert transform - create new`() {
        // Given
        val refAndType = GlobalTransform.RefAndType.newBuilder()
            .setRef("email")
            .setType(GlobalTransform.TransformCase.TAG_TRANSFORM.name)
            .build()

        val newRef = "pipo"
        // When
        val changed =
            GlobalTransform.newBuilder().merge(underTest.getTransform(refAndType)!!.transform!!).build().toBuilder()
                .setRef(newRef)
                .setTagTransform(
                    TagTransform.newBuilder()
                        .setTagContent(newRef)
                ).build()
        underTest.getTransform(changed.refAndType()).shouldBeNull()
        underTest.upsertTransform(changed)
        val readback = underTest.getTransform(changed.refAndType())
        readback.shouldNotBeNull()
        val readbackApi = GlobalTransform.newBuilder().merge(readback.transform!!).build()
        readbackApi shouldBe changed
    }

    @Test
    fun `delete transform`() {
        // Given
        val refAndType = GlobalTransform.RefAndType.newBuilder()
            .setRef("email")
            .setType(GlobalTransform.TransformCase.TAG_TRANSFORM.name)
            .build()

        // When
        val actual = underTest.getTransform(refAndType)

        // Then
        actual.shouldNotBeNull()

        underTest.deleteTransform(listOf(refAndType)) shouldBe 1
        underTest.deleteTransform(listOf(refAndType)) shouldBe 0
        underTest.getTransform(refAndType).shouldBeNull()
    }

    companion object {
        @Language("yaml")
        private val emailTransform = """
            ref: email
            description: "A default transform that should be applied to fields tagged with 'email'."
            tag_transform:
              tag_content: email
              transforms:
                - principals: []
                  fixed:
                    value: "***@***.***"
            """.trimIndent().yaml2json().parseTransform()

        @Language("yaml")
        private val nameTransform = """
            ref: name
            description: "A default transform that should be applied to fields tagged with 'name'."
            tag_transform:
              tag_content: name
              transforms:
                - principals: []
                  nullify: {}
            """.trimIndent().yaml2json().parseTransform()

        private fun GlobalTransform.toRecord(): GlobalTransformsRecord {
            val record = GlobalTransformsRecord()
            record.ref = this.ref
            record.transformType = this.transformCase.name
            record.transform = this.toJsonbWithDefaults()
            record.active = true
            return record
        }

        private fun List<GlobalTransform>.toRecords(): List<GlobalTransformsRecord> = this.map { it.toRecord() }

        private fun GlobalTransformsRecord.withoutTimestamps() = this.apply {
            this.updatedAt = null
            this.createdAt = null
        }

        private fun List<GlobalTransformsRecord>.withoutTimestamps() = this.map { it.withoutTimestamps() }
    }
}
