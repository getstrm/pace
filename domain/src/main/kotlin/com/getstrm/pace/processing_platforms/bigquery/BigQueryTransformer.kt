package com.getstrm.pace.processing_platforms.bigquery

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

class BigQueryTransformer(
    customJooqSettings: Settings.() -> Unit = {},
) : ProcessingPlatformTransformer {
    /**
     * BigQuery requires backticked names for certain names, and MySQL dialect uses backticks, so we
     * abuse this here.
     */
    private val bigQueryDsl: DSLContext =
        DSL.using(SQLDialect.MYSQL, defaultJooqSettings.apply(customJooqSettings))

    override fun renderName(name: String) = bigQueryDsl.renderNamedParams(DSL.quotedName(name))

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
}
