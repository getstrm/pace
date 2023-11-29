package com.getstrm.pace.plugins.data_policy_generators

import build.buf.gen.getstrm.pace.plugins.data_policy_generators.v1alpha.OpenAIDataPolicyGeneratorPayload
import com.getstrm.pace.config.PluginConfiguration
import com.getstrm.pace.config.PluginOpenAIConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import com.google.protobuf.Any as ProtoAny

@TestPropertySource(locations = ["classpath:openai.properties"])
@ExtendWith(SpringExtension::class)
// Todo: we have to think about how we will test (openai) plugins
@Disabled
class OpenAIDataPolicyGeneratorTest {
    @Value("\${app.plugins.openai.api-key}")
    private lateinit var apiKey: String


    @Test
    fun generateYaml() {
        runBlocking {
            val generator = OpenAIDataPolicyGenerator(
                PluginConfiguration(PluginOpenAIConfiguration(apiKey))
            )
            generator.generate(ProtoAny.pack(OpenAIDataPolicyGeneratorPayload.getDefaultInstance()))

//            generator.generateYaml(
//                """
//        For the group  administrators, do not filter the data. For all other users, only show records with email that end with google.com.
//        For the group administrators, replace the username with a fixed value of "omitted". For the group analytics, hash the organization with a seed of banana. For all other users, use a regex pattern to only show the domain of the email field.
//    """.trimIndent()
//            )
        }
    }
}
