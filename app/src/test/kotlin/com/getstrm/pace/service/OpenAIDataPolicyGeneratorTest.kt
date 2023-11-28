package com.getstrm.pace.service

import com.getstrm.pace.config.PluginConfiguration
import com.getstrm.pace.config.PluginOpenAIConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension


@TestPropertySource(locations = ["classpath:openai.yaml"])
@ExtendWith(SpringExtension::class)
class OpenAIDataPolicyGeneratorTest {
    @Value("\${app.plugins.openai.api-key}")
    private lateinit var apiKey: String

    @Test
    fun generateYaml() {
        runBlocking {
            val generator = OpenAIDataPolicyGenerator(
                PluginConfiguration(PluginOpenAIConfiguration(apiKey))
            )

            generator.generateYaml(
                """
        For the group  administrators, do not filter the data. For all other users, only show records with email that end with google.com.
        For the group administrators, replace the username with a fixed value of "omitted". For the group analytics, hash the organization with a seed of banana. For all other users, use a regex pattern to only show the domain of the email field.
    """.trimIndent()
            )
        }
    }
}
