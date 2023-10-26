package com.getstrm.pace.databricks

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.databricks.sdk.service.catalog.TableInfo
import com.getstrm.pace.util.toTimestamp

fun TableInfo.toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy =
    DataPolicy.newBuilder()
        .setInfo(
            DataPolicy.Info.newBuilder()
                .setTitle(name)
                .setDescription(comment.orEmpty())
                .setCreateTime(createdAt.toTimestamp())
                .setUpdateTime(updatedAt.toTimestamp()),
        )
        .setPlatform(platform)
        .setSource(
            DataPolicy.Source.newBuilder()
                .setRef(fullName)
                .setType(DataPolicy.Source.Type.DATABRICKS)
                .addAllAttributes(
                    columns.map { column ->
                        DataPolicy.Attribute.newBuilder()
                            .addAllPathComponents(listOf(column.name))
                            .setType(column.typeText)
                            .setRequired(!(column.nullable ?: false))
                            .build()
                    },
                )
                .build(),
        )
        .build()
