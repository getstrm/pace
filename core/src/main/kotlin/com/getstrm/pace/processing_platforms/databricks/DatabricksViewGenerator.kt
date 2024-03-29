package com.getstrm.pace.processing_platforms.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.UNIMPLEMENTED
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.google.rpc.DebugInfo
import org.jooq.Condition
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class DatabricksViewGenerator(
    dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {}
) : ProcessingPlatformViewGenerator(
    dataPolicy,
    transformer = DatabricksTransformer,
    customJooqSettings = customJooqSettings,
) {
    override fun toPrincipalCondition(
        principals: List<DataPolicy.Principal>,
        target: DataPolicy.Target?
    ): Condition? {
        return if (principals.isEmpty()) {
            null
        } else {
            DSL.or(
                principals.map { principal ->
                    when {
                        principal.hasGroup() ->
                            DSL.condition("is_account_group_member({0})", principal.group)
                        else ->
                            throw InternalException(
                                InternalException.Code.INTERNAL,
                                DebugInfo.newBuilder()
                                    .setDetail(
                                        "Principal of type ${principal.principalCase} is not supported for platform Databricks. $UNIMPLEMENTED"
                                    )
                                    .build()
                            )
                    }
                }
            )
        }
    }
}
