package com.getstrm.daps.snowflake

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.daps.common.AbstractDynamicViewGenerator
import com.getstrm.daps.common.Principal
import org.jooq.Condition
import org.jooq.Queries
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class SnowflakeDynamicViewGenerator(
    dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {},
) : AbstractDynamicViewGenerator(dataPolicy, customJooqSettings) {

    override fun List<Principal>.toPrincipalCondition(): Condition? {
        return if (isEmpty()) {
            null
        } else {
            DSL.or(
                map { principal ->
                    DSL.condition("IS_ROLE_IN_SESSION({0})", principal)
                }
            )
        }
    }

    override fun additionalFooterStatements(): Queries {
        val grants = dataPolicy.ruleSetsList.flatMap { ruleSet ->
            val principals =
                ruleSet.fieldTransformsList.flatMap { it.transformsList }.flatMap { it.principalsList }.toSet() +
                        ruleSet.rowFiltersList.flatMap { it.conditionsList }.flatMap { it.principalsList }.toSet()

            val viewName = ruleSet.target.fullname

            principals.map {
                DSL.query(jooq.grant(DSL.privilege("SELECT")).on(DSL.table(DSL.unquotedName(viewName))).to(DSL.role(it)).sql)
            }
        }

        return jooq.queries(grants)
    }
}
