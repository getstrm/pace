package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.throwUnimplemented
import com.getstrm.pace.processing_platforms.CAPTURING_GROUP_REGEX
import com.getstrm.pace.processing_platforms.ProcessingPlatformRenderer
import com.getstrm.pace.processing_platforms.ProcessingPlatformTransformer
import com.getstrm.pace.util.defaultJooqSettings
import com.getstrm.pace.util.fullName
import com.getstrm.pace.util.toJooqField
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL

object BigQueryTransformer : ProcessingPlatformTransformer(BigQueryRenderer) {

    override fun regexpReplace(
        field: DataPolicy.Field,
        regexp: DataPolicy.RuleSet.FieldTransform.Transform.Regexp
    ): Field<*> =
        if (regexp.replacement.isNullOrEmpty()) {
            DSL.field(
                "regexp_extract({0}, {1})",
                String::class.java,
                DSL.field(field.fullName(), String::class.java),
                DSL.`val`(regexp.regexp),
            )
        } else {
            // Bigquery expects two backslashes for the capturing group notation, here doubled
            // because of
            // Kotlin escaping.
            val replacementWithBackslashNotation =
                regexp.replacement.replace(CAPTURING_GROUP_REGEX, """\\\\$1""")
            DSL.field(
                "regexp_replace({0}, {1}, {2})",
                String::class.java,
                DSL.field(field.fullName(), String::class.java),
                DSL.`val`(regexp.regexp),
                DSL.`val`(replacementWithBackslashNotation),
            )
        }

    override fun hash(
        field: DataPolicy.Field,
        hash: DataPolicy.RuleSet.FieldTransform.Transform.Hash
    ): Field<*> {
        val dataType = field.toJooqField().dataType
        // If the field is a string, we use SHA256, otherwise we use FARM_FINGERPRINT.
        return if (dataType.isNumeric) {
            DSL.field(
                "FARM_FINGERPRINT(CAST({0} AS STRING))",
                Any::class.java,
                DSL.field(DSL.unquotedName(renderName(field.fullName()))),
            )
        } else if (dataType.isString) {
            DSL.field(
                "TO_HEX(SHA256(CAST({0} AS STRING)))",
                Any::class.java,
                DSL.field(DSL.unquotedName(renderName(field.fullName()))),
            )
        } else {
            throwUnimplemented("Hashing a ${dataType.typeName} type")
        }
    }

    private object BigQueryRenderer : ProcessingPlatformRenderer {

        /**
         * BigQuery requires backticked names for certain names, and MySQL dialect uses backticks,
         * so we use this here.
         */
        private val dslContext = DSL.using(SQLDialect.MYSQL, defaultJooqSettings())

        override fun renderName(name: String): String =
            dslContext.renderNamedParams(DSL.quotedName(name))
    }
}
