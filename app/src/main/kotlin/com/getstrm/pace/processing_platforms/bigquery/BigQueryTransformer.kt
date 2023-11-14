package com.getstrm.pace.processing_platforms.bigquery

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
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
     * BigQuery requires backticked names for certain names, and MySQL dialect uses backticks, so we abuse this here.
     */
    private val bigQueryDsl: DSLContext =
        DSL.using(SQLDialect.MYSQL, defaultJooqSettings.apply(customJooqSettings))

    override fun detokenize(
        field: DataPolicy.Field,
        detokenize: DataPolicy.RuleSet.FieldTransform.Transform.Detokenize,
        sourceRef: String
    ): Field<String> {
        return DSL.field(
            "coalesce({0}, {1})",
            String::class.java,
            DSL.unquotedName("${renderName(detokenize.tokenSourceRef)}.${detokenize.valueField.fullName()}"),
            DSL.unquotedName("${renderName(sourceRef)}.${field.fullName()}"),
        )
    }

    private fun renderName(name: String) = bigQueryDsl.renderNamedParams(DSL.quotedName(name))
}
