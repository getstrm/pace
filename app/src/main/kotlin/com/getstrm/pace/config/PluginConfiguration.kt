package com.getstrm.pace.config

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "app.plugins")
data class PluginConfiguration(
    val openai: PluginOpenAIConfiguration? = null,
)

data class PluginOpenAIConfiguration(
    val apiKey: String,
)


