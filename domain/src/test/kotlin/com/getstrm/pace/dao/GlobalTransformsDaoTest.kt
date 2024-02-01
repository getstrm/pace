package com.getstrm.pace.dao

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Nullify.getDefaultInstance
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.TagTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.TransformCase.TAG_TRANSFORM
import com.getstrm.jooq.generated.tables.records.GlobalTransformsRecord
import com.getstrm.pace.AbstractDatabaseTest
import com.getstrm.pace.config.GlobalTransformsConfiguration
import com.getstrm.pace.config.TagTransforms
import com.getstrm.pace.util.*
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlobalTransformsDaoTest : AbstractDatabaseTest() {
    private val underTest = GlobalTransformsDao(jooq, GlobalTransformsConfiguration())

    @BeforeEach
    fun setupDatabase() {
        dataSource.executeMigrations("database/global-transforms")
    }

    @Test
    fun `get transform - exists`() {
        // Given some transforms in the database
        // When
        val actual = underTest.getTagTransform("email")

        // Then
        actual.shouldNotBeNull()
        actual.toGlobalTransform() shouldBe emailTransform
    }

    @Test
    fun `get transform - does not exist`() {
        // Given a transform that does not exist
        // When
        val actual = underTest.getTagTransform("does not exist")

        // Then
        actual.shouldBeNull()
    }

    @Test
    fun `list transforms`() {
        // Given a transform type
        val transformType = TAG_TRANSFORM

        // When all transforms are listed
        val actual = underTest.listTransforms(transformType)

        // Then
        actual shouldHaveSize 2
        actual.withoutTimestamps() shouldContainExactlyInAnyOrder
            listOf(emailTransform, nameTransform).toRecords()
    }

    @Test
    fun `upsert transform - update existing`() {
        // Given an update for an existing transform
        val updateToEmailTransform =
            emailTransform
                .toBuilder()
                .apply {
                    tagTransform =
                        tagTransform
                            .toBuilder()
                            .apply {
                                addAllTransforms(
                                    transformsList.map {
                                        it.toBuilder().setNullify(getDefaultInstance()).build()
                                    }
                                )
                            }
                            .build()
                }
                .build()

        // When
        val actual = underTest.upsertTransform(updateToEmailTransform)

        // Then
        actual.shouldNotBeNull()
        actual.withoutTimestamps() shouldBe updateToEmailTransform.toRecord()
    }

    @Test
    fun `upsert transform - create new`() {
        // Given

        val newRef = "pipo"
        // When
        val changed =
            underTest
                .getTagTransform("email")!!
                .toGlobalTransform()
                .toBuilder()
                .setTagTransform(TagTransform.newBuilder().setTagContent(newRef))
                .build()
        underTest.getTransform(changed.refAndType()).shouldBeNull()
        underTest.upsertTransform(changed)
        val readback = underTest.getTransform(changed.refAndType())
        readback.shouldNotBeNull()
        readback.toGlobalTransform() shouldBe changed
    }

    @Test
    fun `upsert transform - check loose tag match`() {
        // Given
        val refOriginal = "This tests-all_loose ends"
        val transform =
            GlobalTransform.newBuilder()
                .setTagTransform(
                    TagTransform.newBuilder()
                        .setTagContent(refOriginal)
                        .addTransforms(
                            FieldTransform.Transform.newBuilder().setNullify(getDefaultInstance())
                        )
                )
                .build()

        // When
        underTest.upsertTransform(transform).toGlobalTransform() shouldBe transform
        underTest.listTransforms().size shouldBe 3
        // Then
        // with loose tag matching, this is considered equal to s1 above.
        // this is the default AppConfiguration
        val refUpdated = "this_tests ALL-loose-ends"
        val readback = underTest.getTagTransform(refUpdated)?.toGlobalTransform()
        readback shouldNotBe null
        readback!!.tagTransform.transformsList.first().transformCase shouldBe
            FieldTransform.Transform.TransformCase.NULLIFY
        // just changed the instance, did not add a new one!
        // so we get the original ref
        readback.refAndType().first shouldBe refOriginal
        readback.tagTransform.tagContent shouldBe refOriginal
        underTest.listTransforms().size shouldBe 3

        val updatedTransform =
            with(transform.toBuilder()) {
                tagTransformBuilder.tagContent = refUpdated
                build()
            }
        underTest.upsertTransform(updatedTransform).toGlobalTransform() shouldBe updatedTransform
        underTest.listTransforms().size shouldBe 3
    }

    @Test
    fun `upsert transform - check strict tag match`() {
        val strictDao =
            GlobalTransformsDao(
                jooq,
                GlobalTransformsConfiguration(TagTransforms(looseTagMatch = false))
            )
        // Given
        val s = "This tests-all_loose ends"
        val transform =
            GlobalTransform.newBuilder()
                .setTagTransform(
                    TagTransform.newBuilder()
                        .setTagContent(s)
                        .addTransforms(
                            FieldTransform.Transform.newBuilder().setNullify(getDefaultInstance())
                        )
                )
                .build()

        // When
        strictDao.upsertTransform(transform).toGlobalTransform() shouldBe transform
        strictDao.listTransforms().size shouldBe 3
        // Then
        val s2 = "this_tests ALL-loose-ends"
        val readback = strictDao.getTagTransform(s2)?.toGlobalTransform()
        readback shouldBe null
        // just changed the instance
        strictDao.listTransforms().size shouldBe 3
    }

    @Test
    fun `delete transform`() {
        // Given
        // When
        val actual = underTest.getTagTransform("email")

        // Then
        actual.shouldNotBeNull()

        underTest.deleteTransform("email", TAG_TRANSFORM) shouldBe 1
        underTest.deleteTransform("email", TAG_TRANSFORM) shouldBe 0
        underTest.getTagTransform("email").shouldBeNull()
    }

    companion object {
        @Language("yaml")
        private val emailTransform =
            """
                description: "A default transform that should be applied to fields tagged with 'email'."
                tag_transform:
                  tag_content: email
                  transforms:
                    - principals: []
                      fixed:
                        value: "***@***.***"
                """
                .trimIndent()
                .toProto<GlobalTransform>()

        @Language("yaml")
        private val nameTransform =
            """
                description: "A default transform that should be applied to fields tagged with 'name'."
                tag_transform:
                  tag_content: name
                  transforms:
                    - principals: []
                      nullify: {}
                """
                .trimIndent()
                .toProto<GlobalTransform>()

        private fun GlobalTransform.toRecord(): GlobalTransformsRecord {
            val record = GlobalTransformsRecord()
            record.ref = this.refAndType().first
            record.transformType = this.transformCase.name
            record.transform = this.toJsonbWithDefaults()
            record.active = true
            return record
        }

        private fun List<GlobalTransform>.toRecords(): List<GlobalTransformsRecord> =
            this.map { it.toRecord() }

        private fun GlobalTransformsRecord.withoutTimestamps() =
            this.apply {
                this.updatedAt = null
                this.createdAt = null
            }

        private fun List<GlobalTransformsRecord>.withoutTimestamps() =
            this.map { it.withoutTimestamps() }
    }
}
