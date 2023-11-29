package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.plugins.v1alpha.*
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.PreconditionFailedException
import com.getstrm.pace.plugins.Plugin
import com.getstrm.pace.plugins.data_policy_generators.DataPolicyGeneratorPlugin
import com.google.rpc.BadRequest
import com.google.rpc.PreconditionFailure
import net.devh.boot.grpc.server.service.GrpcService
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.Plugin as ApiPlugin

@GrpcService
class PluginsApi(private val plugins: List<Plugin>) : PluginsServiceGrpcKt.PluginsServiceCoroutineImplBase() {
    private val apiPlugins = plugins.map {
        ApiPlugin.newBuilder()
            .setId(it.id)
            .setPluginType(it.type)
            .setImplementation(it::class.java.canonicalName)
            .build()
    }

    private val pluginsByType = plugins.groupBy { it.type }
    private val pluginsById = plugins.associateBy { it.id }

    override suspend fun listPlugins(request: ListPluginsRequest): ListPluginsResponse =
        ListPluginsResponse.newBuilder()
            .addAllPlugins(apiPlugins)
            .build()

    override suspend fun getPayloadDescriptor(request: GetPayloadDescriptorRequest): GetPayloadDescriptorResponse {
        val plugin = pluginsById[request.pluginId] ?: throw pluginNotFoundException(pluginId = request.pluginId)

        return GetPayloadDescriptorResponse.newBuilder()
            .setPayloadDescriptor(plugin.payloadDescriptor.toProto())
            .build()
    }

    override suspend fun invokeDataPolicyGenerator(request: InvokeDataPolicyGeneratorRequest): InvokeDataPolicyGeneratorResponse {
        return pluginsByType[PluginType.DATA_POLICY_GENERATOR]?.let { plugins ->
            val plugin = getDataPolicyGeneratorPlugin(request, plugins)

            InvokeDataPolicyGeneratorResponse.newBuilder()
                .setDataPolicy(plugin.generate(request.payload))
                .build()
        } ?: throw pluginNotFoundException(PluginType.DATA_POLICY_GENERATOR)
    }

    private fun getDataPolicyGeneratorPlugin(
        request: InvokeDataPolicyGeneratorRequest,
        plugins: List<Plugin>
    ) = if (request.hasPluginId()) {
        plugins.firstOrNull { it.id == request.pluginId } as DataPolicyGeneratorPlugin
    } else {
        when (plugins.size) {
            0 -> null
            1 -> plugins.first() as DataPolicyGeneratorPlugin
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
    } ?: throw pluginNotFoundException(PluginType.DATA_POLICY_GENERATOR, request.pluginId)

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
