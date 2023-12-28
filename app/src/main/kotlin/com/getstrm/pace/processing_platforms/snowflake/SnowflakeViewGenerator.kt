package com.getstrm.pace.processing_platforms.snowflake

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.UNIMPLEMENTED
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.getstrm.pace.util.uniquePrincipals
import com.google.rpc.DebugInfo
import org.jooq.Condition
import org.jooq.Queries
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class SnowflakeViewGenerator(
    dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {},
) :
    ProcessingPlatformViewGenerator(
        dataPolicy,
        transformer = SnowflakeTransformer(),
        customJooqSettings = customJooqSettings
    ) {
    override fun toPrincipalCondition(principals: List<DataPolicy.Principal>): Condition? {
        return if (principals.isEmpty()) {
            null
        } else {
            DSL.or(
                principals.map { principal ->
                    when {
                        principal.hasGroup() ->
                            DSL.condition("IS_ROLE_IN_SESSION({0})", principal.group)
                        else ->
                            throw InternalException(
                                InternalException.Code.INTERNAL,
                                DebugInfo.newBuilder()
                                    .setDetail(
                                        "Principal of type ${principal.principalCase} is not supported for platform Snowflake. $UNIMPLEMENTED"
                                    )
                                    .build()
                            )
                    }
                }
            )
        }
    }

    override fun additionalFooterStatements(): Queries {
        val grants =
            dataPolicy.ruleSetsList.flatMap { ruleSet ->
                val viewName = ruleSet.target.fullname

                ruleSet.uniquePrincipals().map {
                    DSL.query(
                        jooq
                            .grant(DSL.privilege("SELECT"))
                            .on(DSL.table(DSL.unquotedName(viewName)))
                            .to(DSL.role(it.group))
                            .sql
                    )
                }
            }

        return jooq.queries(grants)
    }
}
