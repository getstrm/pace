package com.getstrm.pace.bigquery

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.google.cloud.bigquery.Field.Mode
import com.google.cloud.bigquery.Table
import com.google.cloud.bigquery.TableDefinition
import com.getstrm.pace.util.toFullName
import com.getstrm.pace.util.toTimestamp

fun Table.toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy {
    // The reload ensures all metadata is fetched, including the schema
    val table = reload()
    return DataPolicy.newBuilder()
        .setInfo(
            DataPolicy.Info.newBuilder()
                .setTitle(this.toFullName())
                .setDescription(table.description.orEmpty())
                .setCreateTime(table.creationTime.toTimestamp())
                .setUpdateTime(table.lastModifiedTime.toTimestamp())
        )
        .setPlatform(platform)
        .setSource(
            DataPolicy.Source.newBuilder()
                .setRef(this.toFullName())
                .setType(DataPolicy.Source.Type.BIGQUERY)
                .addAllAttributes(table.getDefinition<TableDefinition>().schema?.fields.orEmpty().map { field ->
                    // Todo: add support for nested fields using getSubFields()
                    DataPolicy.Attribute.newBuilder()
                        .addPathComponents(field.name)
                        .setType(field.type.name())
                        // Todo: correctly handle repeated fields (defined by mode REPEATED)
                        .setRequired(field.mode != Mode.NULLABLE)
                        .build()
                })
                .build()
        )
        .build()
}
