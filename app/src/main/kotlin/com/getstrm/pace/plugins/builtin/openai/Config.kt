package com.getstrm.pace.plugins.builtin.openai

import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import io.ktor.client.plugins.logging.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.Duration.Companion.seconds

@Configuration
@EnableConfigurationProperties(OpenAIConfig::class)
@ConditionalOnProperty("app.plugins.openai.enabled", havingValue = "true")
class Config {
    @Bean
    fun openAIDataPolicyGenerator(
        config: OpenAIConfig,
    ): OpenAIPlugin {
        val openAI = OpenAI(
            token = config.apiKey,
            timeout = Timeout(socket = 60.seconds)
        ) {
            install(Logging) {
                logger = Logger.DEFAULT
            }
        }
        return OpenAIPlugin(openAI, ModelId(config.model))
    }
}

@ConfigurationProperties(prefix = "app.plugins.openai")
class OpenAIConfig(
    val apiKey: String,
    val model: String = "gpt-3.5-turbo",
)
