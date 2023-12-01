package com.getstrm.pace.plugins

import build.buf.gen.getstrm.pace.api.plugins.v1alpha.PluginType

interface Plugin {
    val id: String
    val type: PluginType
    val payloadJsonSchema: String
}
