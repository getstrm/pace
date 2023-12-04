package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.plugins.v1alpha.DataPolicyGeneratorResult
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.InvokePluginRequest
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.InvokePluginResponse
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.PluginType
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PreconditionFailedException
import com.getstrm.pace.plugins.Plugin
import com.getstrm.pace.plugins.data_policy_generators.DataPolicyGeneratorPlugin
import com.getstrm.pace.util.toPluginType
import com.google.rpc.BadRequest
import com.google.rpc.DebugInfo
import com.google.rpc.PreconditionFailure
import org.springframework.stereotype.Component
import kotlin.reflect.KClass
import kotlin.reflect.cast
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.Plugin as ApiPlugin

@Component
class PluginsService(plugins: List<Plugin>) {
    private val pluginsByType = plugins.groupBy { it.type }
    private val pluginsById = plugins.associateBy { it.id }

    private val apiPlugins = plugins.map {
        build.buf.gen.getstrm.pace.api.plugins.v1alpha.Plugin.newBuilder()
            .setId(it.id)
            .setPluginType(it.type)
            .setImplementation(it::class.java.canonicalName)
            .build()
    }

    fun listPlugins(): List<ApiPlugin> = apiPlugins

    fun getPluginPayloadJsonSchema(pluginId: String): String =
        (pluginsById[pluginId] ?: throw pluginNotFoundException(pluginId = pluginId)).payloadJsonSchema

    suspend fun invokePlugin(request: InvokePluginRequest): InvokePluginResponse {
        val pluginType = request.parametersCase.toPluginType()
        val pluginsForType = pluginsByType[pluginType] ?: throw pluginNotFoundException(pluginType)

        val responseBuilder = InvokePluginResponse.newBuilder()

        return when (pluginType) {
            PluginType.DATA_POLICY_GENERATOR -> {
                val plugin = getPlugin(request.pluginId, pluginsForType, DataPolicyGeneratorPlugin::class)
                val dataPolicy = plugin.generate(request.dataPolicyGeneratorParameters.payload)

                responseBuilder.setDataPolicyGeneratorResult(
                    DataPolicyGeneratorResult.newBuilder().setDataPolicy(dataPolicy)
                )
            }

            else -> throw InternalException(
                InternalException.Code.INTERNAL,
                DebugInfo.newBuilder()
                    .setDetail("No mapping configured for plugin type $pluginType to a plugin class.")
                    .build()
            )
        }.build()
    }

    private fun <T : Plugin> getPlugin(
        pluginId: String?,
        plugins: List<Plugin>,
        clazz: KClass<T>
    ) = if (!pluginId.isNullOrEmpty()) {
        clazz.cast(plugins.firstOrNull { it.id == pluginId })
    } else {
        when (plugins.size) {
            0 -> null
            1 -> clazz.cast(plugins.first())
            else -> throw BadRequestException(
                BadRequestException.Code.INVALID_ARGUMENT,
                BadRequest.newBuilder()
                    .addFieldViolations(
                        BadRequest.FieldViolation.newBuilder()
                            .setField("plugin_id")
                            .setDescription("Multiple plugins found for ${PluginType.DATA_POLICY_GENERATOR.name}. Please specify a plugin ID.")
                            .build()
                    )
                    .build()
            )
        }
    } ?: throw pluginNotFoundException(PluginType.DATA_POLICY_GENERATOR, pluginId)


    private fun pluginNotFoundException(pluginType: PluginType? = null, pluginId: String? = null) =
        PreconditionFailedException(
            PreconditionFailedException.Code.FAILED_PRECONDITION,
            PreconditionFailure.newBuilder()
                .addViolations(
                    PreconditionFailure.Violation.newBuilder()
                        .setType("plugin")
                        .apply { pluginId?.let { setSubject(it) } }
                        .setDescription(
                            "Plugin not found or configured." + pluginType?.let {
                                " Ensure that your PACE instance has an implementation for ${it.name}."
                            }.orEmpty()
                        )
                        .build()
                )
                .build()
        )
}
