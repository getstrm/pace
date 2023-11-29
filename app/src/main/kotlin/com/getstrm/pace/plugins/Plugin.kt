package com.getstrm.pace.plugins

import build.buf.gen.getstrm.pace.api.plugins.v1alpha.PluginType
import com.google.protobuf.Descriptors.Descriptor

interface Plugin {
    val id: String
    val type: PluginType
    val payloadDescriptor: Descriptor
}
