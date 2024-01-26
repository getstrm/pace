package com.getstrm.pace.processing_platforms.h2

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.processing_platforms.ProcessingPlatformTransformer
import com.getstrm.pace.util.defaultJooqSettings
import com.getstrm.pace.util.fullName
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class H2Transformer(
    private val customJooqSettings: Settings.() -> Unit = {},
) : ProcessingPlatformTransformer {

    private val h2DSL = DSL.using(SQLDialect.H2, defaultJooqSettings.apply(customJooqSettings))

    override fun renderName(name: String): String {
        return h2DSL.renderNamedParams(DSL.quotedName(name))
    }

    override fun regexpReplace(
        field: DataPolicy.Field,
        regexp: DataPolicy.RuleSet.FieldTransform.Transform.Regexp
    ): Field<*> =
        if (regexp.replacement.isNullOrEmpty()) {
            DSL.field(
                "regexp_substr({0}, {1})",
                String::class.java,
                DSL.field(renderName(field.fullName()), String::class.java),
                DSL.`val`(regexp.regexp),
            )
        } else {
            // H2 uses dollar notation for capturing groups, so we can keep the replacement as is.
            DSL.field(
                "regexp_replace({0}, {1}, {2})",
                String::class.java,
                DSL.field(renderName(field.fullName()), String::class.java),
                DSL.`val`(regexp.regexp),
                DSL.`val`(regexp.replacement),
            )
        }
}
