package com.getstrm.pace.processing_platforms.databricks

import SynapseTransformer
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.GenericFilter
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.UNIMPLEMENTED
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.getstrm.pace.util.fullName
import com.getstrm.pace.util.headTailFold
import com.getstrm.pace.util.uniquePrincipals
import com.google.rpc.DebugInfo
import org.jooq.Condition
import org.jooq.CreateViewAsStep
import org.jooq.DatePart
import org.jooq.Queries
import org.jooq.Record
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import java.sql.Timestamp
import kotlin.math.min
import org.jooq.Field as JooqField

class SynapseViewGenerator(
    dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {}
) : ProcessingPlatformViewGenerator(
    dataPolicy,
    transformer = SynapseTransformer(),
    customJooqSettings = customJooqSettings
) {
    fun grantSelectPrivileges(): Queries {
        val grants = dataPolicy.ruleSetsList.flatMap { ruleSet ->
            val viewName = ruleSet.target.fullname

            ruleSet.uniquePrincipals().map {
                DSL.query(
                    jooq.grant(DSL.privilege("SELECT")).on(DSL.table(DSL.unquotedName(renderName(viewName))))
                        .to(DSL.role(DSL.quotedName(it.group))).sql
                )
            }
        }

        return jooq.queries(grants)
    }

    override fun createOrReplaceView(name: String): CreateViewAsStep<Record> = jooq.createView(name)

    fun dropViewsSQL() = jooq.queries(dataPolicy.ruleSetsList.map {
        jooq.dropViewIfExists(renderName(it.target.fullname))
    })

    /**
     * If the database name is included in the ref, we need to drop it.
     * The sql query for Synapse only supports <schema.table>.
     */
    override fun renderName(name: String): String {
        val reference = name.split(".", limit = 3)
        return super.renderName(
            reference
                .drop(min(reference.size - 2, 1))
                .joinToString(".")
        )
    }


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
     * Synapse does not support the direct use of booleans in the where clause. The conditions need to evaluate to
     * either true or false, but cannot be the reserved keywords true or false. Hence, the replacement with 1=1 and 1=0.
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

            it.condition = DSL.condition(
                DSL.`when`(DSL.condition(synapseCondition), 1)
                    .otherwise(0).toString()
            ).toString()
        }
        with(builder.build()) {
            if (this.conditionsList.size == 1) {
                return DSL.condition("1 = ({0})", DSL.unquotedName(this.conditionsList.first().condition))
            } else {
                return DSL.condition("1 = ({0})", super.toCondition(this))
            }
        }
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
            DSL.field("{0}", Int::class.java, MAX_RETENTION)
        }
    }

    companion object {
        const val MAX_RETENTION = 100000
    }
}
