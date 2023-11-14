package com.getstrm.pace.processing_platforms.snowflake

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.processing_platforms.CAPTURING_GROUP_REGEX
import com.getstrm.pace.processing_platforms.ProcessingPlatformTransformer
import com.getstrm.pace.util.fullName
import org.jooq.Field
import org.jooq.impl.DSL

class SnowflakeTransformer : ProcessingPlatformTransformer {

    override fun regexpReplace(
        field: DataPolicy.Field,
        regexp: DataPolicy.RuleSet.FieldTransform.Transform.Regexp
    ): Field<*> =
        if (regexp.replacement.isNullOrEmpty()) {
            DSL.field(
                "regexp_extract({0}, {1})",
                String::class.java,
                DSL.unquotedName(field.fullName()),
                DSL.`val`(regexp.regexp),
            )
        } else {
            // Postgres expects two backslashes for the capturing group notation, here doubled because of Kotlin escaping.
            val replacementWithBackslashNotation = regexp.replacement.replace(CAPTURING_GROUP_REGEX, """\\\\$1""")
            DSL.regexpReplaceAll(
                DSL.field(field.fullName(), String::class.java),
                regexp.regexp,
                replacementWithBackslashNotation,
            )
        }
}
