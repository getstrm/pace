package io.strmprivacy.management.data_policy_service.bigquery

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.daps.common.AbstractDynamicViewGenerator
import com.getstrm.daps.common.Principal
import org.jooq.*
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DSL.select

class BigQueryDynamicViewGenerator(
    dataPolicy: DataPolicy,
    private val userGroupsTable: String,
    customJooqSettings: Settings.() -> Unit = {},
) : AbstractDynamicViewGenerator(dataPolicy, customJooqSettings) {

    /**
     * BigQuery requires backticked names for certain names, and MySQL dialect uses backticks, so we abuse this here.
     */
    private val bigQueryDsl = DSL.using(SQLDialect.MYSQL)

    override fun List<Principal>.toPrincipalCondition(): Condition? {
        return if (isEmpty()) {
            null
        } else {
            DSL.or(
                map { principal ->
                    DSL.condition("{0} IN ( SELECT userGroup FROM user_groups )", principal)
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
