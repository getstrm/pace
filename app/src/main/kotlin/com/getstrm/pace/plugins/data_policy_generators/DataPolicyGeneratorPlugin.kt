package com.getstrm.pace.plugins.data_policy_generators

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.plugins.Plugin
import com.google.protobuf.Any as ProtoAny

interface DataPolicyGeneratorPlugin : Plugin {

    suspend fun generate(payload: ProtoAny): DataPolicy
}
