package com.getstrm.pace.dao

import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.RefAndType
import com.getstrm.jooq.generated.tables.records.GlobalTransformsRecord
import com.getstrm.jooq.generated.tables.references.GLOBAL_TRANSFORMS
import com.getstrm.pace.config.AppConfiguration
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.util.refAndType
import com.getstrm.pace.util.toJsonbWithDefaults
import com.getstrm.pace.util.toOffsetDateTime
import com.google.rpc.DebugInfo
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.row
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class GlobalTransformsDao(
    private val jooq: DSLContext,
        private val appConfiguration: AppConfiguration
) {

    fun getTransform(refAndType: RefAndType): GlobalTransformsRecord? = let {
        // TODO handle non-loose case
        jooq.select()
            .from(GLOBAL_TRANSFORMS)
            .where(
                GLOBAL_TRANSFORMS.TRANSFORM_TYPE.eq(refAndType.type).and(
                        if(appConfiguration.looseTagMatch) {
                            GLOBAL_TRANSFORMS.REF.likeIgnoreCase(refAndType.toLooseMatch())
                        } else {
                            GLOBAL_TRANSFORMS.REF.eq(refAndType.ref)
                        }
                )
            )
            .orderBy(GLOBAL_TRANSFORMS.TRANSFORM_TYPE, GLOBAL_TRANSFORMS.REF)
            .fetchInto(GLOBAL_TRANSFORMS)
            .firstOrNull()
    }



    fun listTransforms(transformType: GlobalTransform.TransformCase? = null): List<GlobalTransformsRecord> =
        jooq.select()
            .from(GLOBAL_TRANSFORMS)
            .apply { if (transformType != null) where(GLOBAL_TRANSFORMS.TRANSFORM_TYPE.eq(transformType.name)) }
            .fetchInto(GLOBAL_TRANSFORMS)

    fun upsertTransform(globalTransform: GlobalTransform): GlobalTransformsRecord {
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

        return getTransform(globalTransform.refAndType()) ?: throw InternalException(
            InternalException.Code.INTERNAL,
            DebugInfo.newBuilder()
                .setDetail("Failed to upsert transform, record was not found after upsert")
                .build()
        )
    }

    fun deleteTransform(refAndTypes: List<RefAndType>): Int {
        return jooq.delete(GLOBAL_TRANSFORMS)
            .where(
                row(GLOBAL_TRANSFORMS.REF, GLOBAL_TRANSFORMS.TRANSFORM_TYPE)
                    .`in`(refAndTypes.map { row(it.ref, it.type) }),
            ).execute()
    }
}

/**
 * returns SQL like style loose match. Underscore is any character.
 */
private fun RefAndType.toLooseMatch() = this.ref.replace(" ", "_").replace("-", "_")
