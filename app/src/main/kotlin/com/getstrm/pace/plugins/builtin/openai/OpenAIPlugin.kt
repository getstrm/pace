package com.getstrm.pace.plugins.builtin.openai

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.Action
import build.buf.gen.getstrm.pace.plugins.data_policy_generators.v1alpha.OpenAIDataPolicyGeneratorPayload
import build.buf.gen.getstrm.pace.plugins.sample_data_generator.v1alpha.OpenAISampleDataGeneratorPayload
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.plugins.GenerateDataPolicyAction
import com.getstrm.pace.plugins.GenerateSampleDataAction
import com.getstrm.pace.plugins.Plugin
import com.getstrm.pace.plugins.PluginAction
import com.getstrm.pace.util.getJSONSchema
import com.getstrm.pace.util.toDDL
import com.getstrm.pace.util.toProto
import com.getstrm.pace.util.toYaml
import com.google.protobuf.InvalidProtocolBufferException
import com.google.rpc.BadRequest
import com.google.rpc.DebugInfo
import org.slf4j.LoggerFactory

class OpenAIPlugin(
    private val openAI: OpenAI
) : Plugin {
    private val log by lazy { LoggerFactory.getLogger(OpenAIPlugin::class.java) }

    override val id = "openai"
    override val actions: Map<Action.Type, PluginAction> =
        listOf(GenerateDataPolicy(), GenerateSampleData()).associateBy { it.type }

    private val dataPolicyJsonSchema = DataPolicy.getDescriptor().getJSONSchema()

    inner class GenerateDataPolicy : GenerateDataPolicyAction {
        override val payloadJsonSchema: String = OpenAIDataPolicyGeneratorPayload.getDescriptor().getJSONSchema()

        override suspend fun invoke(payload: String): DataPolicy {
            val generatorPayload = payload.toProto<OpenAIDataPolicyGeneratorPayload>()

            return try {
                when (generatorPayload.dataPolicyCase) {
                    OpenAIDataPolicyGeneratorPayload.DataPolicyCase.INITIAL_DATA_POLICY ->
                        generate(generatorPayload.instructions, generatorPayload.initialDataPolicy)

                    OpenAIDataPolicyGeneratorPayload.DataPolicyCase.DATAPOLICY_NOT_SET, null ->
                        throw invalidPayloadTypeNotSet()
                }
            } catch (e: InvalidProtocolBufferException) {
                throw invalidPayload(e)
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
            return convertResponse(response) { responseContent ->
                responseContent.toProto<DataPolicy>().also {
                    log.debug("Parsed DataPolicy: {}", it)
                }
            }
        }
    }

    inner class GenerateSampleData : GenerateSampleDataAction {
        override val payloadJsonSchema: String = OpenAISampleDataGeneratorPayload.getDescriptor().getJSONSchema()
        override suspend fun invoke(payload: String): String {
            val generatorPayload = payload.toProto<OpenAISampleDataGeneratorPayload>()

            return try {
                when (generatorPayload.sourceDetailsCase) {
                    OpenAISampleDataGeneratorPayload.SourceDetailsCase.SOURCE ->
                        generateData(generatorPayload.source, generatorPayload.additionalSystemInstructionsList)

                    OpenAISampleDataGeneratorPayload.SourceDetailsCase.SOURCEDETAILS_NOT_SET, null ->
                        throw invalidPayloadTypeNotSet()
                }
            } catch (e: InvalidProtocolBufferException) {
                throw invalidPayload(e)
            }
        }

        private suspend fun generateData(
            source: DataPolicy.Source,
            additionalSystemInstructions: List<String> = emptyList()
        ): String {
            val systemMessage = ChatMessage(
                role = ChatRole.System,
                content = """
                    You are presented with a DDL query of SQL table. Your task is to generate sample data, using the column names and types.
                    Only respond with data, presented as CSV. Do not include any formatting. Ensure to properly double quote each field in the CSV. Always include the header with the column names.
                    
                    Additional instructions:
                    
                """.trimIndent() + additionalSystemInstructions.joinToString(prefix = "-", separator = "\n"),
            )

            val ddlMessage = ChatMessage(
                role = ChatRole.User,
                content = source.toDDL()
            )

            val request = ChatCompletionRequest(
                model = ModelId("gpt-4-1106-preview"),
                messages = listOf(
                    systemMessage,
                    ddlMessage,
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
            return convertResponse(response) { it }
        }
    }

    private fun invalidPayload(e: InvalidProtocolBufferException) = BadRequestException(
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

    private fun invalidPayloadTypeNotSet() = BadRequestException(
        BadRequestException.Code.INVALID_ARGUMENT,
        BadRequest.newBuilder()
            .addFieldViolations(
                BadRequest.FieldViolation.newBuilder()
                    .setField("payload")
                    .setDescription("Invalid payload")
                    .build()
            )
            .build()
    )


    private fun <T> convertResponse(response: ChatCompletion, resultConverter: (String) -> T): T {
        return when (val messageContent = response.choices.first().message.messageContent) {
            is TextContent -> {
                try {
                    resultConverter(messageContent.content)
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
