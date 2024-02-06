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
                val viewName = ruleSet.target.ref.integrationFqn

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

    override fun renderName(name: String): String {
        if (name.contains('.')) {
            // Full references containing schema and table name should be quoted separately
            return name.split('.').joinToString(".") { jooq.renderNamedParams(DSL.quotedName(it)) }
        }

        return jooq.renderNamedParams(DSL.quotedName(name))
    }

    override fun selectWithAdditionalHeaderStatements(
        fields: List<Field<*>>
    ): SelectSelectStep<Record> {
        val userGroupSelect =
            DSL.quotedName("user_groups")
                .`as`(
                    DSL.select(DSL.field(DSL.quotedName("rolname")))
                        .from(DSL.table(DSL.quotedName("pg_roles")))
                        .where(
                            DSL.field(DSL.quotedName("rolcanlogin"))
                                .eq(DSL.inline(false))
                                .and(
                                    DSL.function(
                                        "pg_has_role",
                                        Boolean::class.java,
                                        DSL.field(DSL.unquotedName("session_user")),
                                        DSL.field(DSL.unquotedName("oid")),
                                        DSL.inline("member")
                                    )
                                )
                        )
                )

        return DSL.with(userGroupSelect).select(fields)
    }
}
