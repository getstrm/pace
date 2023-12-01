package com.getstrm.pace.plugins.data_policy_generators.openai

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.plugins.data_policy_generators.v1alpha.OpenAIDataPolicyGeneratorPayload
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.plugins.data_policy_generators.DataPolicyGeneratorPlugin
import com.getstrm.pace.util.getJSONSchema
import com.getstrm.pace.util.parseDataPolicy
import com.getstrm.pace.util.toProto
import com.getstrm.pace.util.toYaml
import com.google.protobuf.InvalidProtocolBufferException
import com.google.rpc.BadRequest
import com.google.rpc.DebugInfo
import org.slf4j.LoggerFactory

class OpenAIDataPolicyGenerator(
    private val openAI: OpenAI
) : DataPolicyGeneratorPlugin {

    private val log by lazy { LoggerFactory.getLogger(OpenAIDataPolicyGenerator::class.java) }

    private val dataPolicyJsonSchema = DataPolicy.getDescriptor().getJSONSchema()

    override val payloadJsonSchema: String = OpenAIDataPolicyGeneratorPayload.getDescriptor().getJSONSchema()
    override val id = "openai-data-policy-generator"

    override suspend fun generate(payload: String): DataPolicy {
        val generatorPayload = payload.toProto<OpenAIDataPolicyGeneratorPayload>()

        return try {
            when (generatorPayload.dataPolicyCase) {
                OpenAIDataPolicyGeneratorPayload.DataPolicyCase.INITIAL_DATA_POLICY ->
                    generate(generatorPayload.instructions, generatorPayload.initialDataPolicy)

                OpenAIDataPolicyGeneratorPayload.DataPolicyCase.DATAPOLICY_NOT_SET, null -> {
                    throw BadRequestException(
                        BadRequestException.Code.INVALID_ARGUMENT,
                        BadRequest.newBuilder()
                            .addFieldViolations(
                                BadRequest.FieldViolation.newBuilder()
                                    .setField("payload")
                                    .setDescription("Invalid payload: data policy not set")
                                    .build()
                            )
                            .build()
                    )
                }
            }
        } catch (e: InvalidProtocolBufferException) {
            throw BadRequestException(
                BadRequestException.Code.INVALID_ARGUMENT,
                BadRequest.newBuilder()
                    .addFieldViolations(
                        BadRequest.FieldViolation.newBuilder()
                            .setField("payload")
                            .setDescription("Invalid payload: ${e.message}")
                            .build()
                    )
                    .build()
            )
        }
    }

    private suspend fun generate(instructions: String, dataPolicy: DataPolicy): DataPolicy {
        val systemMessage = ChatMessage(
            role = ChatRole.System,
            content = "Your task is to create a YAML file based on a JSON Schema and instructions by the end user. Ensure that you only respond with the YAML as plain text, do not provide any other details.",
        )

        val jsonSchemaMessage = ChatMessage(
            role = ChatRole.User,
            content = dataPolicyJsonSchema,
        )

        val blueprintDataPolicyMessage = ChatMessage(
            role = ChatRole.User,
            content = """
                Use the following blueprint data policy as a starting point, and add a single rule set to it based on the instructions provided.
                ${dataPolicy.toYaml()}
            """.trimIndent()
        )

        val instructionsMessage = ChatMessage(
            role = ChatRole.User,
            content = """$instructions
                |
                |Make sure to return the YAML as plain text, without any formatting code blocks.
            """.trimMargin()
        )

        val request = ChatCompletionRequest(
            model = ModelId("gpt-4-1106-preview"),
            messages = listOf(
                systemMessage,
                jsonSchemaMessage,
                blueprintDataPolicyMessage,
                instructionsMessage
            ),
            seed = 42, // https://platform.openai.com/docs/guides/text-generation/reproducible-outputs
            maxTokens = 4095,
            temperature = 0.0,
            topP = 1.0,
            frequencyPenalty = 0.0,
            presencePenalty = 0.0
        )
        log.trace("Request: {}", request)
        val response = openAI.chatCompletion(request)
        log.trace("Response: {}", response)
        return convertResponse(response)
    }

    private fun convertResponse(response: ChatCompletion): DataPolicy {
        return when (val messageContent = response.choices.first().message.messageContent) {
            is TextContent -> {
                try {
                    messageContent.content.parseDataPolicy().also {
                        log.debug("Parsed DataPolicy: {}", it)
                    }
                } catch (e: Exception) {
                    throw InternalException(
                        InternalException.Code.INTERNAL,
                        DebugInfo.newBuilder()
                            .setDetail("Could not parse DataPolicy YAML generated by OpenAI: ${e.message}").build(),
                        e,
                    )
                }
            }

            else -> {
                throw InternalException(
                    InternalException.Code.INTERNAL,
                    DebugInfo.newBuilder().setDetail("Unexpected OpenAI response: $messageContent").build(),
                )
            }
        }
    }
}
