package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.common.AbstractDynamicViewGenerator
import org.jooq.Condition
import org.jooq.Field
import org.jooq.conf.ParamType
import org.jooq.impl.DSL

// We wrap the field in a select to get a sql with bound values
fun Field<*>.toSql(): String = DSL.select(this).getSQL(ParamType.INLINED).removePrefix("select ")

class TestDynamicViewGenerator(dataPolicy: DataPolicy) : AbstractDynamicViewGenerator(dataPolicy) {
    override fun List<DataPolicy.Principal>.toPrincipalCondition(): Condition? {
        return null
    }
}

fun String.toPrincipal() = DataPolicy.Principal.newBuilder().setGroup(this).build()
fun List<String>.toPrincipals() = map { it.toPrincipal() }
