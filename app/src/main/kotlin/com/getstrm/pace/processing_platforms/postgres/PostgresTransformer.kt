package com.getstrm.pace.processing_platforms.postgres

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.processing_platforms.CAPTURING_GROUP_REGEX
import com.getstrm.pace.processing_platforms.ProcessingPlatformTransformer
import com.getstrm.pace.util.fullName
import org.jooq.Field
import org.jooq.impl.DSL

class PostgresTransformer : ProcessingPlatformTransformer {

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
