package com.getstrm.pace.plugins

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.Action

interface Plugin {
    val id: String
    val actions: Map<Action.Type, PluginAction>
}

interface PluginAction {
    val actionName: String
    val type: Action.Type
    val invokable: Boolean
    val payloadJsonSchema: String?
}

interface GenerateDataPolicyAction : PluginAction {
    override val actionName: String
        get() = "generate-data-policy"

    override val type: Action.Type
        get() = Action.Type.GENERATE_DATA_POLICY

    override val invokable: Boolean
        get() = true

    suspend fun invoke(payload: String): DataPolicy
}

interface GenerateSampleDataAction : PluginAction {
    override val actionName: String
        get() = "generate-sample-data"
    override val type: Action.Type
        get() = Action.Type.GENERATE_SAMPLE_DATA
    override val invokable: Boolean
        get() = true

    suspend fun invoke(payload: String): String
}
