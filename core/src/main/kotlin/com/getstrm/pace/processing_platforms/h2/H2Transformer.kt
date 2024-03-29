package com.getstrm.pace.processing_platforms.h2

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.throwUnimplemented
import com.getstrm.pace.processing_platforms.ProcessingPlatformRenderer
import com.getstrm.pace.processing_platforms.ProcessingPlatformTransformer
import com.getstrm.pace.util.defaultJooqSettings
import com.getstrm.pace.util.fullName
import com.getstrm.pace.util.toJooqField
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL

object H2Transformer : ProcessingPlatformTransformer(H2Renderer) {

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

    private object H2Renderer : ProcessingPlatformRenderer {
        private val dslContext = DSL.using(SQLDialect.H2, defaultJooqSettings())

        override fun renderName(name: String): String = dslContext.renderNamedParams(DSL.name(name))
    }

    override fun hash(
        field: DataPolicy.Field,
        hash: DataPolicy.RuleSet.FieldTransform.Transform.Hash
    ): Field<*> {
        val dataType = field.toJooqField().dataType
        return if (dataType.isNumeric) {
            DSL.field(
                "ORA_HASH({0})",
                Any::class.java,
                DSL.unquotedName(renderName(field.fullName())),
            )
        } else if (dataType.isString) {
            DSL.field(
                "HASH('SHA-256', CAST({0} as varchar))",
                Any::class.java,
                DSL.unquotedName(renderName(field.fullName())),
            )
        } else {
            throwUnimplemented("Hashing a ${dataType.typeName} type")
        }
    }
}
