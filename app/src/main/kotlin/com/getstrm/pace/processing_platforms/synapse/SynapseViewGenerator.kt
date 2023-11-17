package com.getstrm.pace.processing_platforms.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.FilterCase.GENERIC_FILTER
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.GenericFilter
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

    /**

    where
    -- use exclude all groups that have already passed
    where
    1 = (case
    when (IS_ROLEMEMBER('fraud_and_risk') = 1) then (case when 1=1 then 1 else 0 end)
    else (case when transactionamount < 100 then 1 else 0 end) end)
    and
    DATEADD(day, (
    select case
    when IS_ROLEMEMBER('fraud_and_risk') = 1 then 10000
    when IS_ROLEMEMBER('marketing') = 1 then 3
    else 10 end
    ), ts) > CURRENT_TIMESTAMP
     */
    override fun createWhereStatement(ruleSet: DataPolicy.RuleSet): List<Condition> {
        ruleSet.filtersList.filter { it.filterCase == GENERIC_FILTER }
        TODO()
    }

    override fun toCondition(filter: GenericFilter): Condition {
        val c = super.toCondition(with(filter.toBuilder()) {
            conditionsList.map {
                with(it.toBuilder()) {
                    condition = "(CASE WHEN $condition THEN 1 ELSE 0 END )"
                    build()
                }
            }
            build()
        })
        return c
    }
}
