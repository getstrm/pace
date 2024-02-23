package com.getstrm.pace.dbt

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar


class DbtRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // Register serialization
        hints.serialization().registerType(DbtModel::class.java)
        hints.serialization().registerType(Column::class.java)
    }
}
