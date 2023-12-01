package com.getstrm.pace.plugins.data_policy_generators

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.plugins.v1alpha.PluginType
import com.getstrm.pace.plugins.Plugin

interface DataPolicyGeneratorPlugin : Plugin {
    override val type: PluginType
        get() = PluginType.DATA_POLICY_GENERATOR

    suspend fun generate(payload: String): DataPolicy
}
