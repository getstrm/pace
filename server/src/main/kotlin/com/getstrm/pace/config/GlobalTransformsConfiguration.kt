package com.getstrm.pace.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.global-transforms")
data class GlobalTransformsConfiguration(val tagTransforms: TagTransforms = TagTransforms())

data class TagTransforms(
    val looseTagMatch: Boolean = true,
)
