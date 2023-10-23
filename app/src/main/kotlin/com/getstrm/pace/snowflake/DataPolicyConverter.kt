package com.getstrm.pace.snowflake

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy


fun SnowflakeResponse.toDataPolicy(platform: DataPolicy.ProcessingPlatform, snowflakeTable: SnowflakeTable): DataPolicy {
    return DataPolicy.newBuilder()
        .setInfo(
            DataPolicy.Info.newBuilder()
//                .setTitle(name)
//                .setDescription(comment.orEmpty())
//                .setCreateTime(createdAt.toTimestamp())
//                .setUpdateTime(updatedAt.toTimestamp())
        )
        .setPlatform(platform)
        .setSource(
            DataPolicy.Source.newBuilder()
                .setRef(snowflakeTable.fullName)
                .setType(DataPolicy.Source.Type.SNOWFLAKE)
                .addAllAttributes(data!!.map { (name, type, _, nullable) ->
                    DataPolicy.Attribute.newBuilder()
                        .addAllPathComponents(listOf(name))
                        .setType(type)
                        .setRequired(nullable != "y")
                        .build()
                })
                .build()
        )
        .build()
}
