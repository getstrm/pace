package com.getstrm.pace.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.common.AbstractDynamicViewGenerator
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException
import com.google.rpc.DebugInfo
import org.jooq.Condition
import org.jooq.Queries
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class PostgresDynamicViewGenerator(
    dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {},
) : AbstractDynamicViewGenerator(dataPolicy, customJooqSettings) {

    private val postgresDsl = DSL.using(SQLDialect.POSTGRES)
    override fun additionalFooterStatements(): Queries {
        val grants = dataPolicy.ruleSetsList.flatMap { ruleSet ->
            val principals =
                ruleSet.fieldTransformsList.flatMap { it.transformsList }.flatMap { it.principalsList }.toSet() +
                        ruleSet.filtersList.flatMap { it.conditionsList }.flatMap { it.principalsList }.toSet()

            val viewName = ruleSet.target.fullname

            principals.map {
                DSL.query(
                    postgresDsl.grant(DSL.privilege("SELECT")).on(DSL.table(DSL.unquotedName(viewName)))
                        .to(DSL.role(it.group)).sql
                )
            }
        }

        return postgresDsl.queries(grants)
    }

    override fun List<DataPolicy.Principal>.toPrincipalCondition(): Condition? {
        return if (isEmpty()) {
            null
        } else {
            DSL.or(
                map { principal ->
                    when {
                        principal.hasGroup() -> DSL.condition(
                            "{0} IN ( SELECT rolname FROM pg_roles WHERE not rolcanlogin and pg_has_role( (select session_user), oid, 'member') )",
                            principal.group
                        )

                        else -> throw InternalException(
                            InternalException.Code.INTERNAL,
                            DebugInfo.newBuilder()
                                .setDetail("Principal of type ${principal.principalCase} is not supported for platform PostgreSQL. ${PaceStatusException.UNIMPLEMENTED}")
                                .build()
                        )
                    }
                })
        }
    }

    override fun renderName(name: String): String = postgresDsl.renderNamedParams(DSL.unquotedName(name))
}
