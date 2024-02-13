package com.getstrm.pace.util

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import com.getstrm.jooq.generated.tables.records.DataPoliciesRecord
import com.getstrm.jooq.generated.tables.records.GlobalTransformsRecord
import com.google.protobuf.util.JsonFormat

fun DataPoliciesRecord.toApiDataPolicy(): DataPolicy =
    this.policy!!.let {
        with(DataPolicy.newBuilder()) {
            JsonFormat.parser().ignoringUnknownFields().merge(it.data(), this)
            build()
        }
    }

fun GlobalTransformsRecord.toGlobalTransform() =
    GlobalTransform.newBuilder().merge(this.transform!!).build()
