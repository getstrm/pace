package com.getstrm.pace.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.databricks.sdk.service.catalog.TableInfo
import com.getstrm.pace.util.toTimestamp

fun TableInfo.toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy =
    DataPolicy.newBuilder()
        .setMetadata(
            DataPolicy.Metadata.newBuilder()
                .setTitle(name)
                .setDescription(comment.orEmpty())
                .setCreateTime(createdAt.toTimestamp())
                .setUpdateTime(updatedAt.toTimestamp()),
        )
        .setPlatform(platform)
        .setSource(
            DataPolicy.Source.newBuilder()
                .setRef(fullName)
                .addAllFields(
                    columns.map { column ->
                        DataPolicy.Field.newBuilder()
                            .addAllNameParts(listOf(column.name))
                            .setType(column.typeText)
                            .setRequired(!(column.nullable ?: false))
                            .build()
                    },
                )
                .build(),
        )
        .build()
