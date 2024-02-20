package com.getstrm.pace.processing_platforms.snowflake

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.processing_platforms.CAPTURING_GROUP_REGEX
import com.getstrm.pace.processing_platforms.ProcessingPlatformRenderer
import com.getstrm.pace.processing_platforms.ProcessingPlatformTransformer
import com.getstrm.pace.util.fullName
import com.getstrm.pace.util.toJooqField
import org.jooq.Field
import org.jooq.impl.DSL

object SnowflakeTransformer : ProcessingPlatformTransformer(ProcessingPlatformRenderer.DEFAULT) {

    override fun regexpReplace(
        field: DataPolicy.Field,
        regexp: DataPolicy.RuleSet.FieldTransform.Transform.Regexp
    ): Field<*> =
        if (regexp.replacement.isNullOrEmpty()) {
            DSL.field(
                "regexp_substr({0}, {1})",
                String::class.java,
                DSL.field(field.fullName(), String::class.java),
                DSL.`val`(regexp.regexp),
            )
        } else {
            // Snowflake expects two backslashes for the capturing group notation, here doubled
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
        val hashField =
            if (hash.hasSeed()) {
                DSL.field(
                    "hash({0}, {1})",
                    Any::class.java,
                    DSL.unquotedName(renderName(field.fullName())),
                    DSL.`val`(hash.seed),
                )
            } else {
                DSL.field(
                    "hash({0})",
                    Any::class.java,
                    DSL.unquotedName(renderName(field.fullName())),
                )
            }
        return if (field.toJooqField().dataType.isNumeric) {
            hashField
        } else {
            DSL.cast(hashField, String::class.java)
        }
    }
}
