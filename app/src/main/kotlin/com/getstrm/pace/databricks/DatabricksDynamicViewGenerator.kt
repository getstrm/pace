package com.getstrm.pace.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.common.AbstractDynamicViewGenerator
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.UNIMPLEMENTED
import com.google.rpc.DebugInfo
import org.jooq.Condition
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class DatabricksDynamicViewGenerator(
    dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {}
) : AbstractDynamicViewGenerator(
    dataPolicy,
    customJooqSettings
) {
    override fun List<DataPolicy.Principal>.toPrincipalCondition(): Condition? {
        return if (isEmpty()) {
            null
        } else {
            DSL.or(
                map { principal ->
                    when {
                        principal.hasGroup() -> DSL.condition("is_account_group_member({0})", principal.group)
                        else -> throw InternalException(
                            InternalException.Code.INTERNAL,
                            DebugInfo.newBuilder()
                                .setDetail("Principal of type ${principal.principalCase} is not supported for platform Databricks. $UNIMPLEMENTED")
                                .build()
                        )
                    }
                }
            )
        }
    }
}
