package com.getstrm.pace.util

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import org.jooq.DataType
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.conf.ParseNameCase
import org.jooq.conf.ParseUnknownFunctions
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.slf4j.LoggerFactory

private val log by lazy { LoggerFactory.getLogger("JooqUtils") }


val defaultJooqSettings: Settings = Settings()
    // This makes sure we can use platform-specific functions (or UDFs)
    .withParseUnknownFunctions(ParseUnknownFunctions.IGNORE)
    // This follows the exact naming from the data policy's field names
    .withParseNameCase(ParseNameCase.AS_IS)
    // This ensures that we explicitly need to quote names
    .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_UNQUOTED)

fun DataPolicy.Field.sqlDataType(): DataType<*> =
    try {
        if (type.lowercase() == "struct") {
            SQLDataType.RECORD
        } else {
            sqlParser.parseField("a::$type").dataType.sqlDataType!!
        }
    } catch (e: Exception) {
        log.warn("Can't parse {}, default to VARCHAR", type)
        SQLDataType.VARCHAR
    }

val sqlParser = DSL.using(SQLDialect.DEFAULT).parser()

fun DataPolicy.Field.normalizeType(): DataPolicy.Field =
    toBuilder().setType(sqlDataType().typeName).build()

fun DataPolicy.Field.toJooqField(): Field<*> = DSL.field(namePartsList.joinToString("."), sqlDataType())
