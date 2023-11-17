package com.getstrm.pace.processing_platforms.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.UNIMPLEMENTED
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.getstrm.pace.util.fullName
import com.google.rpc.DebugInfo
import org.jooq.Condition
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class SynapseViewGenerator(
    dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {}
) : ProcessingPlatformViewGenerator(dataPolicy, customJooqSettings = customJooqSettings) {
    override fun toPrincipalCondition(principals: List<DataPolicy.Principal>): Condition? {
        return if (principals.isEmpty()) {
            null
        } else {
            DSL.or(
                principals.map { principal ->
                    when {
                        principal.hasGroup() -> DSL.condition("IS_ROLEMEMBER({0})=1", principal.group)
                        else -> throw InternalException(
                            InternalException.Code.INTERNAL,
                            DebugInfo.newBuilder()
                                .setDetail("Principal of type ${principal.principalCase} is not supported for platform Synapse. $UNIMPLEMENTED")
                                .build()
                        )
                    }
                }
            )
        }
    }
}
