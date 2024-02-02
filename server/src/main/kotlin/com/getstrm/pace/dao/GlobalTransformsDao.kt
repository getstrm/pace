package com.getstrm.pace.dao

import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.TransformCase
import com.getstrm.jooq.generated.tables.records.GlobalTransformsRecord
import com.getstrm.jooq.generated.tables.references.GLOBAL_TRANSFORMS
import com.getstrm.pace.config.GlobalTransformsConfiguration
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.util.refAndType
import com.getstrm.pace.util.toJsonbWithDefaults
import com.getstrm.pace.util.toOffsetDateTime
import com.google.rpc.DebugInfo
import java.time.Instant
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class GlobalTransformsDao(
    private val jooq: DSLContext,
    private val globalTransformsConfiguration: GlobalTransformsConfiguration
) {

    fun getTransform(ref: String, type: TransformCase): GlobalTransformsRecord? =
        jooq
            .select()
            .from(GLOBAL_TRANSFORMS)
            .where(
                GLOBAL_TRANSFORMS.TRANSFORM_TYPE.eq(type.name)
                    .and(
                        if (globalTransformsConfiguration.tagTransforms.looseTagMatch) {
                            GLOBAL_TRANSFORMS.REF.likeIgnoreCase(ref.toSqlLikePattern())
                        } else {
                            GLOBAL_TRANSFORMS.REF.eq(ref)
                        }
                    )
            )
            // deterministic ordering
            .orderBy(GLOBAL_TRANSFORMS.TRANSFORM_TYPE, GLOBAL_TRANSFORMS.REF)
            .fetchInto(GLOBAL_TRANSFORMS)
            // the result might contain multiple records, but only by manual hacking
            // in the database. Using the [upsertTransform] method will prevent duplicates.
            .firstOrNull()

    fun getTagTransform(ref: String) = getTransform(ref, TransformCase.TAG_TRANSFORM)

    fun getTransform(pair: Pair<String, TransformCase>) = getTransform(pair.first, pair.second)

    fun listTransforms(transformType: TransformCase? = null): List<GlobalTransformsRecord> =
        jooq
            .select()
            .from(GLOBAL_TRANSFORMS)
            .apply {
                if (transformType != null)
                    where(GLOBAL_TRANSFORMS.TRANSFORM_TYPE.eq(transformType.name))
            }
            .fetchInto(GLOBAL_TRANSFORMS)

    fun upsertTransform(globalTransform: GlobalTransform): GlobalTransformsRecord {
        val now = Instant.now().toOffsetDateTime()
        val (ref, type) = globalTransform.refAndType()
        val record =
            getTransform(ref, type)
                ?: jooq.newRecord(GLOBAL_TRANSFORMS).apply { this.createdAt = now }

        record
            .apply {
                /* ¡Muy importante!
                the statement below means that if we already have a record (primary key is ref), we keep that ref
                even though we might have found it via another tag value (case, underscore, dash or whatever)

                If we wouldn't do this ternary operation, a record found via 'email PII' but originally stored
                via 'Email-pii' would create a new record instead of update the existing one.
                */
                this.ref = if (record.ref.isNullOrEmpty()) ref else record.ref
                this.transformType = globalTransform.transformCase.name
                this.updatedAt = now
                this.transform = globalTransform.toJsonbWithDefaults()
                this.active = true
            }
            .store()

        return getTransform(ref, type)
            ?: throw InternalException(
                InternalException.Code.INTERNAL,
                DebugInfo.newBuilder()
                    .setDetail("Failed to upsert transform, record was not found after upsert")
                    .build()
            )
    }

    fun deleteTransform(ref: String, type: TransformCase): Int =
        getTransform(ref, type)?.delete() ?: 0
}

/**
 * returns SQL like style loose match pattern. Underscore is any character similar to '.' in a
 * regular expression
 */
private fun String.toSqlLikePattern() = this.replace(" ", "_").replace("-", "_")
