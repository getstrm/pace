package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.processing_platforms.CAPTURING_GROUP_REGEX
import com.getstrm.pace.processing_platforms.ProcessingPlatformTransformer
import com.getstrm.pace.util.defaultJooqSettings
import com.getstrm.pace.util.fullName
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class PostgresTransformer(
    customJooqSettings: Settings.() -> Unit = {},
) : ProcessingPlatformTransformer {
    private val postgresDsl: DSLContext =
        DSL.using(SQLDialect.POSTGRES, defaultJooqSettings.apply(customJooqSettings))

    override fun renderName(name: String): String {
        if (name.contains('.')) {
            // Full references containing schema and table name should be quoted separately
            return name.split('.').joinToString(".") {
                postgresDsl.renderNamedParams(DSL.quotedName(it))
            }
        }

        return postgresDsl.renderNamedParams(DSL.quotedName(name))
    }

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
            // of
            // Kotlin escaping.
            val replacementWithBackslashNotation =
                regexp.replacement.replace(CAPTURING_GROUP_REGEX, """\\$1""")
            DSL.regexpReplaceAll(
                DSL.field(field.fullName(), String::class.java),
                DSL.`val`(regexp.regexp),
                DSL.`val`(replacementWithBackslashNotation),
            )
        }
}