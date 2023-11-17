package com.getstrm.pace.processing_platforms.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.GenericFilter
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.UNIMPLEMENTED
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.getstrm.pace.util.fullName
import com.getstrm.pace.util.headTailFold
import com.google.rpc.DebugInfo
import org.jooq.Condition
import org.jooq.DatePart
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import java.sql.Timestamp
import org.jooq.Field as JooqField

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

    override fun toCondition(filter: GenericFilter): Condition {
        val builder = filter.toBuilder()
        builder.conditionsBuilderList.map {
            val synapseCondition = it.condition.let { c ->
                when (c) {
                    "true" -> "1=1"
                    "false" -> "1=0"
                    else -> c
                }
            }
            it.condition = "(CASE WHEN $synapseCondition THEN 1 ELSE 0 END)"
        }
        return DSL.condition("1 = ({0})", super.toCondition(builder.build()))
    }

    override fun toCondition(retention: DataPolicy.RuleSet.Filter.RetentionFilter): Condition {
        val whereCondition = retention.conditionsList.headTailFold(
            headOperation = { condition ->
                DSL.`when`(
                    toPrincipalCondition(condition.principalsList),
                    condition.toSynapseRetentionCondition(),
                )
            },
            bodyOperation = { conditionStep, condition ->
                conditionStep.`when`(
                    toPrincipalCondition(condition.principalsList),
                    condition.toSynapseRetentionCondition(),
                )
            },
            tailOperation = { conditionStep, condition ->
                conditionStep.otherwise(condition.toSynapseRetentionCondition())
            },
        )

        val retentionClause = DSL.field(
            "{0} > {1}",
            Boolean::class.java,
            DSL.timestampAdd(
                DSL.field(DSL.unquotedName(retention.field.fullName()), Timestamp::class.java),
                DSL.field("(${whereCondition})", Int::class.java),
                DatePart.DAY
            ),
            DSL.currentTimestamp()
        )

        return DSL.condition(retentionClause)
    }

    private fun DataPolicy.RuleSet.Filter.RetentionFilter.Condition.toSynapseRetentionCondition(): JooqField<Int> {
        return if (this.hasPeriod()) {
            DSL.field("{0}", Int::class.java, this.period.days)
        } else {
            // this virtually represents no retention.
            DSL.field("{0}", Int::class.java, 10000)
        }
    }
}
