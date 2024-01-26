package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import com.getstrm.pace.util.fullName
import com.google.rpc.DebugInfo
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.SelectSelectStep
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DSL.currentTimestamp
import org.jooq.impl.DSL.select
import org.jooq.impl.DSL.trueCondition

class BigQueryViewGenerator(
    dataPolicy: DataPolicy,
    private val userGroupsTable: String?,
    private val useIamCheckExtension: Boolean = false,
    customJooqSettings: Settings.() -> Unit = {},
) :
    ProcessingPlatformViewGenerator(
        dataPolicy,
        transformer = BigQueryTransformer(),
        customJooqSettings = customJooqSettings
    ) {

    /**
     * BigQuery requires backticked names for certain names, and MySQL dialect uses backticks, so we
     * abuse this here.
     */
    private val bigQueryDsl: DSLContext = DSL.using(SQLDialect.MYSQL)

    init {
        if (userGroupsTable.isNullOrBlank() and !useIamCheckExtension)
            throw InternalException(
                InternalException.Code.INTERNAL,
                DebugInfo.newBuilder()
                    .setDetail(
                        "userGroupsTable must be set if useIamCheckExtension is false. ${PaceStatusException.ILLEGAL_ARGUMENT}"
                    )
                    .build()
            )
    }

    override fun toPrincipalCondition(principals: List<DataPolicy.Principal>): Condition? {
        return if (principals.isEmpty()) {
            null
        } else {
            DSL.or(
                principals.map { principal ->
                    when {
                        principal.hasGroup() ->
                            if (useIamCheckExtension)
                                DSL.condition(
                                    "\"True\" in (select principal_check_routines.check_principal_access({0}))",
                                    DSL.quotedName(principal.group)
                                )
                            else
                                DSL.condition(
                                    "{0} IN ( SELECT {1} FROM {2} )",
                                    principal.group,
                                    field(renderName("userGroup")),
                                    field(renderName("user_groups"))
                                )
                        else ->
                            throw InternalException(
                                InternalException.Code.INTERNAL,
                                DebugInfo.newBuilder()
                                    .setDetail(
                                        "Principal of type ${principal.principalCase} is not supported for platform BigQuery. ${PaceStatusException.UNIMPLEMENTED}"
                                    )
                                    .build()
                            )
                    }
                }
            )
        }
    }

    override fun renderName(name: String): String =
        bigQueryDsl.renderNamedParams(DSL.quotedName(name))

    override fun selectWithAdditionalHeaderStatements(
        fields: List<Field<*>>
    ): SelectSelectStep<Record> {
        if (useIamCheckExtension) return DSL.select(fields)
        val userGroupSelect =
            DSL.unquotedName("user_groups")
                .`as`(
                    select(DSL.field("userGroup"))
                        .from(DSL.table(renderName(userGroupsTable!!)))
                        .where(
                            DSL.field("userEmail")
                                .eq(DSL.function("SESSION_USER", Boolean::class.java))
                        )
                )
        return DSL.with(userGroupSelect).select(fields)
    }

    override fun DataPolicy.RuleSet.Filter.RetentionFilter.Condition.toRetentionCondition(
        field: DataPolicy.Field
    ): Field<Boolean> =
        if (this.hasPeriod()) {
            DSL.field(
                "TIMESTAMP_ADD({0}, INTERVAL {1} DAY) > {2}",
                Boolean::class.java,
                DSL.unquotedName(field.fullName()),
                this.period.days,
                currentTimestamp()
            )
        } else {
            DSL.field(trueCondition())
        }
}
