package com.getstrm.pace.processing_platforms.h2

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import org.jooq.Condition
import org.jooq.conf.Settings
import org.jooq.impl.DSL

/**
 * This in-memory view generator is only used to evaluate data policies on sample data without
 * creating actual views. Instead, the corresponding select statements are executed directly on the
 * sample data for the given principal. The source ref is overridden to point to the in-memory
 * table.
 */
class H2ViewGenerator(
    dataPolicy: DataPolicy,
    private val principalToApply: DataPolicy.Principal?,
    sourceRefOverride: String,
    customJooqSettings: Settings.() -> Unit = {}
) :
    ProcessingPlatformViewGenerator(
        dataPolicy
            .toBuilder()
            .setSource(
                dataPolicy.source
                    .toBuilder()
                    .setRef(resourceUrn { integrationFqn = sourceRefOverride })
                    .build()
            )
            .build(),
        transformer = H2Transformer(),
        customJooqSettings = customJooqSettings
    ) {
    override fun toPrincipalCondition(principals: List<DataPolicy.Principal>): Condition? {
        return if (principals.isEmpty()) {
            null
        } else {
            if (principalToApply != null && principals.contains(principalToApply)) {
                DSL.trueCondition()
            } else {
                DSL.falseCondition()
            }
        }
    }
}
