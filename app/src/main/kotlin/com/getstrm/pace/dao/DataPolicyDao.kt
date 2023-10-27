package com.getstrm.pace.dao

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.jooq.generated.tables.DataPolicies.Companion.DATA_POLICIES
import com.getstrm.jooq.generated.tables.records.DataPoliciesRecord
import com.google.protobuf.util.JsonFormat
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import com.getstrm.pace.util.toJsonb
import com.getstrm.pace.util.toOffsetDateTime
import com.getstrm.pace.util.toTimestamp
import java.time.OffsetDateTime

@Component
class DataPolicyDao(
    private val jooq: DSLContext,
) {
    // Todo: remove context from all methods, and return only the latest version
    fun listDataPolicies(): List<DataPolicy> = jooq.select()
        .from(DATA_POLICIES)
        .fetchInto(DATA_POLICIES)
        .map { it.toApiDataPolicy() }

    // Todo: create versions
    fun upsertDataPolicy(dataPolicy: DataPolicy, dslContext: DSLContext = jooq): DataPolicy {
        val oldPolicy = with(dataPolicy.metadata) {
            getDataPolicy(title, version, dslContext)?.also {
                deactivateDataPolicy(title, version, updateTime.toOffsetDateTime(), dslContext)
            }
        }
        val updateTimestamp = OffsetDateTime.now()
        val updatedPolicy = dataPolicy.toBuilder()
            .setId(oldPolicy?.id ?: dataPolicy.source.ref)
            .setMetadata(
                dataPolicy.metadata.toBuilder()
                    .setUpdateTime(updateTimestamp.toTimestamp())
                    .setCreateTime(oldPolicy?.metadata?.createTime ?: updateTimestamp.toTimestamp())
                    .build()
            ).build()

        dslContext.newRecord(DATA_POLICIES).apply {
            this.policy = updatedPolicy.toJsonb()
            this.id = updatedPolicy.id
            this.platformId = updatedPolicy.platform.id
            this.updatedAt = updateTimestamp
            this.createdAt = updatedPolicy.metadata.createTime.toOffsetDateTime()
            this.active = true
            this.title = updatedPolicy.metadata.title
            this.description = updatedPolicy.metadata.description
            this.version = updatedPolicy.metadata.version
        }.also {
            it.store()
        }

        return updatedPolicy
    }

    private fun deactivateDataPolicy(
        title: String,
        version: String,
        updateTime: OffsetDateTime,
        dslContext: DSLContext = jooq
    ) {
        dslContext.update(DATA_POLICIES)
            .set(DATA_POLICIES.ACTIVE, false)
            .where(DATA_POLICIES.TITLE.eq(title))
            .and(DATA_POLICIES.VERSION.eq(version))
            .and(DATA_POLICIES.UPDATED_AT.eq(updateTime))
            .execute()
    }

    private fun getDataPolicy(title: String, version: String, dslContext: DSLContext = jooq): DataPolicy? {
        return dslContext.select()
            .from(DATA_POLICIES)
            .where(DATA_POLICIES.TITLE.eq(title))
            .and(DATA_POLICIES.VERSION.eq(version))
            .orderBy(DATA_POLICIES.UPDATED_AT.desc())
            .limit(1)
            .fetchOneInto(DATA_POLICIES)
            .toApiDataPolicy()
    }

    fun getLatestDataPolicy(id: String, dslContext: DSLContext = jooq): DataPolicy? {
        return dslContext.select()
            .from(DATA_POLICIES)
            .where(DATA_POLICIES.ID.eq(id))
            .orderBy(DATA_POLICIES.UPDATED_AT.desc())
            .limit(1)
            .fetchOneInto(DATA_POLICIES)
            .toApiDataPolicy()
    }
}

fun DataPoliciesRecord?.toApiDataPolicy(): DataPolicy? = this?.policy?.let {
    with(DataPolicy.newBuilder()) {
        JsonFormat.parser().ignoringUnknownFields().merge(it.data(), this)
        build()
    }
}
