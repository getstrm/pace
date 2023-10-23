package com.getstrm.pace.util

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.pace.common.AbstractDynamicViewGenerator
import com.getstrm.pace.common.Principal
import org.jooq.Condition
import org.jooq.Field
import org.jooq.conf.ParamType
import org.jooq.impl.DSL

// We wrap the field in a select to get a sql with bound values
fun Field<*>.toSql(): String = DSL.select(this).getSQL(ParamType.INLINED).removePrefix("select ")

class TestDynamicViewGenerator(dataPolicy: DataPolicy) : AbstractDynamicViewGenerator(dataPolicy) {
    override fun List<Principal>.toPrincipalCondition(): Condition? {
        return null
    }
}
