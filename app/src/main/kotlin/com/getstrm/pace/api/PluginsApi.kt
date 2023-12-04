package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.plugins.v1alpha.*
import com.getstrm.pace.service.PluginsService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class PluginsApi(private val pluginsService: PluginsService) : PluginsServiceGrpcKt.PluginsServiceCoroutineImplBase() {
    override suspend fun listPlugins(request: ListPluginsRequest): ListPluginsResponse =
        ListPluginsResponse.newBuilder()
            .addAllPlugins(pluginsService.listPlugins())
            .build()

    override suspend fun getPayloadJSONSchema(request: GetPayloadJSONSchemaRequest): GetPayloadJSONSchemaResponse =
        GetPayloadJSONSchemaResponse.newBuilder()
            .setSchema(pluginsService.getPluginPayloadJsonSchema(request.pluginId))
            .build()

    override suspend fun invokePlugin(request: InvokePluginRequest): InvokePluginResponse =
        pluginsService.invokePlugin(request)
}
