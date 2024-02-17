package com.getstrm.pace

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.conf.ParamType
import org.jooq.impl.DSL

// We wrap the field in a select to get a sql with bound values
// Note: this is uses default settings (e.g. dialect), unless passed
fun Field<*>.toSql(context: DSLContext = DSL.using(SQLDialect.DEFAULT)): String =
    context.select(this).getSQL(ParamType.INLINED).removePrefix("select ")

fun namedField(name: String, type: String? = null): DataPolicy.Field =
    DataPolicy.Field.newBuilder()
        .addNameParts(name)
        .apply { if (type != null) setType(type) }
        .build()

fun String.toPrincipal(): DataPolicy.Principal =
    DataPolicy.Principal.newBuilder().setGroup(this).build()

fun List<String>.toPrincipals() = map { it.toPrincipal() }
