package com.getstrm.pace.snowflake

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.util.normalizeType

fun SnowflakeResponse.toDataPolicy(platform: DataPolicy.ProcessingPlatform, fullName: String): DataPolicy {
    return DataPolicy.newBuilder()
        .setMetadata(
            DataPolicy.Metadata.newBuilder()
                .setTitle(fullName),
        )
        .setPlatform(platform)
        .setSource(
            DataPolicy.Source.newBuilder()
                .setRef(fullName)
                .addAllFields(
                    // Todo: make this more type-safe
                    data.orEmpty().map { (name, type, _, nullable) ->
                        DataPolicy.Field.newBuilder()
                            .addAllNameParts(listOf(name))
                            .setType(type)
                            .setRequired(nullable != "y")
                            .build()
                            .normalizeType()
                    },
                )
                .build(),
        )
        .build()
}
