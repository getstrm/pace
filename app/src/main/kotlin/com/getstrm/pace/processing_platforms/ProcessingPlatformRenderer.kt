package com.getstrm.pace.processing_platforms

import com.getstrm.pace.util.defaultJooqSettings
import org.jooq.SQLDialect
import org.jooq.impl.DSL

interface ProcessingPlatformRenderer {
    fun renderName(name: String): String = defaultDslContext.renderNamedParams(DSL.name(name))
    companion object {
        private val defaultDslContext = DSL.using(SQLDialect.DEFAULT, defaultJooqSettings)
    }
}
