package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.processing_platforms.ProcessingPlatformViewGenerator
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.conf.ParamType
import org.jooq.impl.DSL
import org.jooq.impl.DSL.trueCondition

// We wrap the field in a select to get a sql with bound values
// Note: this is uses default settings (e.g. dialect).
fun Field<*>.toSql(): String = DSL.select(this).getSQL(ParamType.INLINED).removePrefix("select ")

fun namedField(name: String, type: String? = null): DataPolicy.Field =
    DataPolicy.Field.newBuilder()
        .addNameParts(name)
        .apply { if (type != null) setType(type) }
        .build()

class TestDynamicViewGenerator(dataPolicy: DataPolicy) :
    ProcessingPlatformViewGenerator(dataPolicy) {
    override val jooq: DSLContext = DSL.using(SQLDialect.DEFAULT)

    override fun toPrincipalCondition(
        principals: List<DataPolicy.Principal>,
        target: DataPolicy.Target?
    ): Condition? {
        return null
    }

    override fun DataPolicy.RuleSet.Filter.RetentionFilter.Condition.toRetentionCondition(
        field: DataPolicy.Field
    ): Condition {
        return trueCondition()
    }
}

fun String.toPrincipal() = DataPolicy.Principal.newBuilder().setGroup(this).build()

fun List<String>.toPrincipals() = map { it.toPrincipal() }
