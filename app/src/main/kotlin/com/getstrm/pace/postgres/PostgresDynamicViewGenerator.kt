package com.getstrm.pace.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.common.AbstractDynamicViewGenerator
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException
import com.google.rpc.DebugInfo
import org.jooq.*
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DSL.select

class PostgresDynamicViewGenerator(
    dataPolicy: DataPolicy,
    private val userGroupsTable: String,
    customJooqSettings: Settings.() -> Unit = {},
) : AbstractDynamicViewGenerator(dataPolicy, customJooqSettings) {

    /**
     * BigQuery requires backticked names for certain names, and MySQL dialect uses backticks, so we abuse this here.
     */
    private val bigQueryDsl = DSL.using(SQLDialect.MYSQL)

    override fun List<DataPolicy.Principal>.toPrincipalCondition(): Condition? {
        return if (isEmpty()) {
            null
        } else {
            DSL.or(
                map { principal ->
                    when {
                        principal.hasGroup() -> DSL.condition(
                            "{0} IN ( SELECT userGroup FROM user_groups )",
                            principal.group
                        )

                        else -> throw InternalException(
                            InternalException.Code.INTERNAL,
                            DebugInfo.newBuilder()
                                .setDetail("Principal of type ${principal.principalCase} is not supported for platform BigQuery. ${PaceStatusException.UNIMPLEMENTED}")
                                .build()
                        )
                    }
                })
        }
    }

    override fun renderName(name: String): String = bigQueryDsl.renderNamedParams(DSL.name(name))

    override fun selectWithAdditionalHeaderStatements(fields: List<Field<*>>): SelectSelectStep<Record> {
        val userGroupSelect = DSL.unquotedName("user_groups")
            .`as`(
                select(
                    DSL.field("userGroup")
                ).from(
                    DSL.table(DSL.unquotedName(userGroupsTable))
                ).where(
                    DSL.field("userEmail").eq(DSL.function("SESSION_USER", Boolean::class.java))
                )
            )
        return DSL.with(userGroupSelect).select(fields)
    }
}
