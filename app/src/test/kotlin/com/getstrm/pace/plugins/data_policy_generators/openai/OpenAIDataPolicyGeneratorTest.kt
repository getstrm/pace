package com.getstrm.pace.plugins.data_policy_generators.openai

import build.buf.gen.getstrm.pace.plugins.data_policy_generators.v1alpha.OpenAIDataPolicyGeneratorPayload
import com.getstrm.pace.util.parseDataPolicy
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import com.google.protobuf.Any as ProtoAny

@TestPropertySource(locations = ["classpath:openai.properties"])
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [Config::class])
// Todo: we have to think about how we will (integration) test (openai) plugins
@Disabled
class OpenAIDataPolicyGeneratorIT {

    @Autowired
    private lateinit var underTest: OpenAIDataPolicyGenerator

    @Test
    fun `OpenAI should generate a valid Data Policy`() {
        runBlocking {
            // Given
            val initialDataPolicy = blueprintDataPolicy.parseDataPolicy()
            val instructions = """
        For the group administrators, do not filter the data. For all other users, only show records with emails ending with google.com.
        For the group administrators, replace the username with a fixed value of "omitted". For the group analytics, hash the organization with a seed of 42. For all other users, use a regex pattern to only show the domain of the email field.
    """.trimIndent()

            // When
            val result = underTest.generate(
                ProtoAny.pack(
                    OpenAIDataPolicyGeneratorPayload.newBuilder()
                        .setInitialDataPolicy(initialDataPolicy)
                        .setInstructions(instructions)
                        .build()
                )
            )

            // Then
            result shouldNotBe initialDataPolicy
            result.ruleSetsList.size shouldBe 1
        }
    }

    companion object {
        @Language("yaml")
        private val blueprintDataPolicy = """
metadata:
  version: 3
  description: "Users of our application"
  title: BANANACORP.USERS
platform:
  id: snowflake-connection
  platform_type: SNOWFLAKE
source:
  ref: BANANACORP.USERS
  fields:
    - name_parts:
        - email
      required: true
      type: varchar
    - name_parts:
        - username
      required: true
      type: varchar
    - name_parts:
        - organization
      required: true
      type: varchar
"""
    }
}
