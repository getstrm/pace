package com.getstrm.pace.snowflake

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy


fun SnowflakeResponse.toDataPolicy(platform: DataPolicy.ProcessingPlatform, snowflakeTable: SnowflakeTable): DataPolicy {
    return DataPolicy.newBuilder()
        .setMetadata(
            DataPolicy.Metadata.newBuilder()
                .setTitle(snowflakeTable.fullName)
        )
        .setPlatform(platform)
        .setSource(
            DataPolicy.Source.newBuilder()
                .setRef(snowflakeTable.fullName)
                .addAllFields(data.orEmpty().map { (name, type, _, nullable) ->
                    DataPolicy.Field.newBuilder()
                        .addAllNameParts(listOf(name))
                        .setType(type)
                        .setRequired(nullable != "y")
                        .build()
                })
                .build()
        )
        .build()
}
