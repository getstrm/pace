package com.getstrm.pace.processing_platforms.bigquery

import com.getstrm.pace.processing_platforms.ProcessingPlatformTransformer
import com.getstrm.pace.util.defaultJooqSettings
import org.jooq.DSLContext
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

    override fun renderName(name: String) = bigQueryDsl.renderNamedParams(DSL.quotedName(name))
}
