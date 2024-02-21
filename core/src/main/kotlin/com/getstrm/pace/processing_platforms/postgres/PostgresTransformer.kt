package com.getstrm.pace.processing_platforms.postgres

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

object PostgresTransformer : ProcessingPlatformTransformer(PostgresRenderer) {

    override fun regexpReplace(
        field: DataPolicy.Field,
        regexp: DataPolicy.RuleSet.FieldTransform.Transform.Regexp
    ): Field<*> =
        if (regexp.replacement.isNullOrEmpty()) {
            DSL.field(
                "substring({0} from {1})",
                String::class.java,
                DSL.field(field.fullName(), String::class.java),
                DSL.`val`(regexp.regexp),
            )
        } else {
            // Postgres expects one backslash for the capturing group notation, here doubled because
            // of Kotlin escaping.
            val replacementWithBackslashNotation =
                regexp.replacement.replace(CAPTURING_GROUP_REGEX, """\\$1""")
            DSL.regexpReplaceAll(
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
        return if (dataType.isNumeric) {
            DSL.field(
                "hashtextextended(cast({0} as varchar), ${if (hash.hasSeed()) hash.seed else 0})",
                String::class.java,
                DSL.field(DSL.unquotedName(renderName(field.fullName()))),
            )
        } else if (dataType.isString) {
            DSL.field(
                "digest(cast({0} as varchar), 'sha256')",
                String::class.java,
                DSL.field(DSL.unquotedName(renderName(field.fullName()))),
            )
        } else {
            throwUnimplemented("Hashing a ${dataType.typeName} type")
        }
    }

    private object PostgresRenderer : ProcessingPlatformRenderer {
        private val dslContext = DSL.using(SQLDialect.POSTGRES, defaultJooqSettings())

        override fun renderName(name: String): String {
            if (name.contains('.')) {
                // Full references containing schema and table name should be quoted separately
                return name.split('.').joinToString(".") {
                    dslContext.renderNamedParams(DSL.quotedName(it))
                }
            }
            return dslContext.renderNamedParams(DSL.quotedName(name))
        }
    }
}
