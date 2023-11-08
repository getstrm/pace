package com.getstrm.pace.dao

import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.RefAndType
import com.getstrm.jooq.generated.tables.records.GlobalTransformsRecord
import com.getstrm.jooq.generated.tables.references.GLOBAL_TRANSFORMS
import com.getstrm.pace.util.refAndType
import com.getstrm.pace.util.toJsonbWithDefaults
import com.getstrm.pace.util.toOffsetDateTime
import org.jooq.DSLContext
import org.jooq.impl.DSL.row
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class GlobalTransformsDao(
    private val jooq: DSLContext
) {
    fun getTransform(refAndType: RefAndType): GlobalTransformsRecord? =
        jooq.select()
            .from(GLOBAL_TRANSFORMS)
            .where(
                GLOBAL_TRANSFORMS.REF.eq(refAndType.ref),
                GLOBAL_TRANSFORMS.TRANSFORM_TYPE.eq(refAndType.type)
            )
            .fetchOneInto(GLOBAL_TRANSFORMS)

    fun listTransforms(transformType: GlobalTransform.TransformCase? = null): List<GlobalTransformsRecord> =
        jooq.select()
            .from(GLOBAL_TRANSFORMS)
            .apply { if (transformType != null) where(GLOBAL_TRANSFORMS.TRANSFORM_TYPE.eq(transformType.name)) }
            .fetchInto(GLOBAL_TRANSFORMS)

    fun upsertTransform(globalTransform: GlobalTransform) {
        val now = Instant.now().toOffsetDateTime()

        val record = getTransform(globalTransform.refAndType()) ?: jooq.newRecord(GLOBAL_TRANSFORMS)
            .apply { this.createdAt = now }

        record.apply {
            this.ref = globalTransform.ref
            this.transformType = globalTransform.transformCase.name
            this.updatedAt = now
            this.transform = globalTransform.toJsonbWithDefaults()
            this.active = true
        }.store()
    }

    fun deleteTransform(refAndTypes: List<RefAndType>): Int {
        return jooq.delete(GLOBAL_TRANSFORMS)
            .where(
                row(GLOBAL_TRANSFORMS.REF, GLOBAL_TRANSFORMS.TRANSFORM_TYPE)
                    .`in`(refAndTypes.map { row(it.ref, it.type) }),
            ).execute()
    }
}
