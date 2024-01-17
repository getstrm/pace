package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.getstrm.pace.util.defaultJooqSettings
import com.getstrm.pace.util.uniquePrincipals
import com.google.rpc.DebugInfo
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Queries
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.SelectSelectStep
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class PostgresViewGenerator(
    dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {},
) :
    ProcessingPlatformViewGenerator(
        dataPolicy,
        transformer = PostgresTransformer(),
        customJooqSettings = customJooqSettings
    ) {
    override val jooq: DSLContext =
        DSL.using(SQLDialect.POSTGRES, defaultJooqSettings.apply(customJooqSettings))

    override fun additionalFooterStatements(): Queries {
        val grants =
            dataPolicy.ruleSetsList.flatMap { ruleSet ->
                val viewName = ruleSet.target.ref.platformFqn

                ruleSet.uniquePrincipals().map {
                    DSL.query(
                        jooq
                            .grant(DSL.privilege("SELECT"))
                            .on(DSL.table(DSL.unquotedName(viewName)))
                            .to(DSL.role(DSL.quotedName(it.group)))
                            .sql
                    )
                }
            }

        return jooq.queries(grants)
    }

    override fun toPrincipalCondition(principals: List<DataPolicy.Principal>): Condition? {
        return if (principals.isEmpty()) {
            null
        } else {
            DSL.or(
                principals.map { principal ->
                    when {
                        principal.hasGroup() ->
                            DSL.condition(
                                "{0} IN ( SELECT rolname FROM user_groups )",
                                principal.group
                            )
                        else ->
                            throw InternalException(
                                InternalException.Code.INTERNAL,
                                DebugInfo.newBuilder()
                                    .setDetail(
                                        "Principal of type ${principal.principalCase} is not supported for platform PostgreSQL. ${PaceStatusException.UNIMPLEMENTED}"
                                    )
                                    .build()
                            )
                    }
                }
            )
        }
    }

    override fun renderName(name: String): String = jooq.renderNamedParams(DSL.unquotedName(name))

    override fun selectWithAdditionalHeaderStatements(
        fields: List<Field<*>>
    ): SelectSelectStep<Record> {
        val userGroupSelect =
            DSL.unquotedName("user_groups")
                .`as`(
                    DSL.select(DSL.field("rolname"))
                        .from(DSL.table(DSL.unquotedName("pg_roles")))
                        .where(
                            DSL.field("rolcanlogin")
                                .eq(false)
                                .and(
                                    DSL.function(
                                        "pg_has_role",
                                        Boolean::class.java,
                                        DSL.field("session_user"),
                                        DSL.field("oid"),
                                        DSL.inline("member")
                                    )
                                )
                        )
                )

        return DSL.with(userGroupSelect).select(fields)
    }
}
