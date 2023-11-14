package com.getstrm.pace.processing_platforms.snowflake

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.UNIMPLEMENTED
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.google.rpc.DebugInfo
import org.jooq.Condition
import org.jooq.Queries
import org.jooq.conf.Settings
import org.jooq.impl.DSL


class SnowflakeViewGenerator(
    dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {},
) : ProcessingPlatformViewGenerator(dataPolicy, customJooqSettings = customJooqSettings) {
    override fun List<DataPolicy.Principal>.toPrincipalCondition(): Condition? {
        return if (isEmpty()) {
            null
        } else {
            DSL.or(
                map { principal ->
                    when {
                        principal.hasGroup() -> DSL.condition("IS_ROLE_IN_SESSION({0})", principal.group)
                        else -> throw InternalException(
                            InternalException.Code.INTERNAL,
                            DebugInfo.newBuilder()
                                .setDetail("Principal of type ${principal.principalCase} is not supported for platform Snowflake. $UNIMPLEMENTED")
                                .build()
                        )
                    }
                }
            )
        }
    }

    override fun additionalFooterStatements(): Queries {
        val grants = dataPolicy.ruleSetsList.flatMap { ruleSet ->
            val principals =
                ruleSet.fieldTransformsList.flatMap { it.transformsList }.flatMap { it.principalsList }.toSet() +
                    ruleSet.filtersList.flatMap { it.conditionsList }.flatMap { it.principalsList }.toSet()

            val viewName = ruleSet.target.fullname

            principals.map {
                DSL.query(
                    jooq.grant(DSL.privilege("SELECT")).on(DSL.table(DSL.unquotedName(viewName)))
                        .to(DSL.role(it.group)).sql
                )
            }
        }

        return jooq.queries(grants)
    }
}
