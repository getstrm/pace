package com.getstrm.pace.plugins.data_policy_generators.openai

import com.aallam.openai.api.http.Timeout
import com.aallam.openai.client.OpenAI
import io.ktor.client.plugins.logging.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.Duration.Companion.seconds

@Configuration
@ConditionalOnProperty("app.plugins.data-policy-generators.openai.api-key")
class Config {
    @Bean
    fun openAIDataPolicyGenerator(
        @Value("\${app.plugins.data-policy-generators.openai.api-key}") apiKey: String,
    ): OpenAIDataPolicyGenerator {
        val openAI = OpenAI(
            token = apiKey,
            timeout = Timeout(socket = 60.seconds)
        ) {
            install(Logging) {
                logger = Logger.DEFAULT
            }
        }
        return OpenAIDataPolicyGenerator(openAI)
    }
}