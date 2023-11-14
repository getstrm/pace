package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.google.rpc.DebugInfo
import org.jooq.*
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DSL.select

class BigQueryViewGenerator(
    dataPolicy: DataPolicy,
    private val userGroupsTable: String,
    customJooqSettings: Settings.() -> Unit = {},
) : ProcessingPlatformViewGenerator(dataPolicy, customJooqSettings) {

    /**
     * BigQuery requires backticked names for certain names, and MySQL dialect uses backticks, so we abuse this here.
     */
    private val bigQueryDsl: DSLContext = DSL.using(SQLDialect.MYSQL, defaultJooqSettings.apply(customJooqSettings))

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

    override fun renderName(name: String): String = bigQueryDsl.renderNamedParams(DSL.quotedName(name))

    override fun selectWithAdditionalHeaderStatements(fields: List<Field<*>>): SelectSelectStep<Record> {
        val userGroupSelect = DSL.unquotedName("user_groups")
            .`as`(
                select(
                    DSL.field("userGroup")
                ).from(
                    DSL.table(renderName(userGroupsTable))
                ).where(
                    DSL.field("userEmail").eq(DSL.function("SESSION_USER", Boolean::class.java))
                )
            )
        return DSL.with(userGroupSelect).select(fields)
    }
}
