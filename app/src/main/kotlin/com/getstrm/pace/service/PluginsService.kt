package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.plugins.v1alpha.Action as ApiAction
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.DataPolicyGenerator
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.InvokePluginRequest
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.InvokePluginResponse
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.Plugin as ApiPlugin
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.SampleDataGenerator
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.PreconditionFailedException
import com.getstrm.pace.plugins.GenerateDataPolicyAction
import com.getstrm.pace.plugins.GenerateSampleDataAction
import com.getstrm.pace.plugins.Plugin
import com.getstrm.pace.plugins.PluginAction
import com.google.rpc.BadRequest
import com.google.rpc.PreconditionFailure
import org.springframework.stereotype.Component

@Component
class PluginsService(plugins: List<Plugin>) {
    private val pluginsById = plugins.associateBy { it.id }

    private val apiPlugins =
        plugins.map {
            ApiPlugin.newBuilder()
                .setId(it.id)
                .addAllActions(
                    it.actions.values.map { action ->
                        ApiAction.newBuilder()
                            .setType(action.type)
                            .setInvokable(action.invokable)
                            .build()
                    }
                )
                .setImplementation(it::class.java.canonicalName)
                .build()
        }

    fun listPlugins(): List<ApiPlugin> = apiPlugins

    fun getPluginPayloadJsonSchema(pluginId: String, action: ApiAction): String =
        getPluginAction(pluginId, action).payloadJsonSchema.orEmpty()

    suspend fun invokePlugin(request: InvokePluginRequest): InvokePluginResponse {
        val action = getPluginAction(request.pluginId, request.action)
        val responseBuilder = InvokePluginResponse.newBuilder()

        when (action) {
            is GenerateDataPolicyAction -> {
                val dataPolicy = action.invoke(request.dataPolicyGeneratorParameters.payload)

                responseBuilder.setDataPolicyGeneratorResult(
                    DataPolicyGenerator.Result.newBuilder().setDataPolicy(dataPolicy)
                )
            }
            is GenerateSampleDataAction -> {
                val sampleData = action.invoke(request.sampleDataGeneratorParameters.payload)

                responseBuilder.setSampleDataGeneratorResult(
                    SampleDataGenerator.Result.newBuilder()
                        .setFormat(SampleDataGenerator.Format.CSV)
                        .setData(sampleData)
                )
            }
        }

        return responseBuilder.build()
    }

    private fun getPluginAction(pluginId: String, action: ApiAction?): PluginAction {
        val plugin = pluginsById[pluginId] ?: throw pluginNotFoundException(pluginId = pluginId)
        return if (action == null) {
            if (plugin.actions.size > 1) {
                throw BadRequestException(
                    BadRequestException.Code.INVALID_ARGUMENT,
                    BadRequest.newBuilder()
                        .addFieldViolations(
                            BadRequest.FieldViolation.newBuilder()
                                .setField("action")
                                .setDescription(
                                    "Invalid action: must be specified when plugin has more than one action"
                                )
                                .build()
                        )
                        .build()
                )
            }
            plugin.actions.values.first()
        } else {
            plugin.actions[action.type] ?: throw pluginNotFoundException(action.type, pluginId)
        }
    }

    private fun pluginNotFoundException(
        actionType: ApiAction.Type? = null,
        pluginId: String? = null
    ) =
        PreconditionFailedException(
            PreconditionFailedException.Code.FAILED_PRECONDITION,
            PreconditionFailure.newBuilder()
                .addViolations(
                    PreconditionFailure.Violation.newBuilder()
                        .setType("plugin")
                        .apply { pluginId?.let { setSubject(it) } }
                        .setDescription(
                            "Plugin not found or configured." +
                                actionType
                                    ?.let {
                                        " Ensure that your PACE instance has an implementation for ${it.name}."
                                    }
                                    .orEmpty()
                        )
                        .build()
                )
                .build()
        )
}
