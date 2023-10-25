package com.getstrm.pace.dao

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.jooq.generated.tables.DataPolicies.Companion.DATA_POLICIES
import com.getstrm.jooq.generated.tables.records.DataPoliciesRecord
import com.google.protobuf.util.JsonFormat
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import toJsonb
import toOffsetDateTime
import toTimestamp
import java.time.OffsetDateTime
import java.util.*

@Component
class DataPolicyDao(
    private val jooq: DSLContext,
) {
    fun listDataPolicies(context: String): List<DataPolicy> = jooq.select()
        .from(DATA_POLICIES)
        // Todo: modify schema, rename to context
        .where(DATA_POLICIES.ORGANIZATION_ID.eq(context))
        .fetchInto(DATA_POLICIES)
        .map { it.toApiDataPolicy() }

    fun upsertDataPolicy(dataPolicy: DataPolicy, context: String, dslContext: DSLContext = jooq): DataPolicy {
        val oldPolicy = with(dataPolicy.info) {
            getDataPolicy(title, context, version, dslContext)?.also {
                deactivateDataPolicy(title, context, version, updateTime.toOffsetDateTime(), dslContext)
            }
        }
        val updateTimestamp = OffsetDateTime.now()
        val updatedPolicy = dataPolicy.toBuilder()
            .setId(oldPolicy?.id ?: UUID.randomUUID().toString())
            .setInfo(
                dataPolicy.info.toBuilder()
                    .setUpdateTime(updateTimestamp.toTimestamp())
                    .setCreateTime(oldPolicy?.info?.createTime ?: updateTimestamp.toTimestamp())
                    .setContext(context)
                    .build()
            ).build()

        dslContext.newRecord(DATA_POLICIES).apply {
            this.policy = updatedPolicy.toJsonb()
            this.id = updatedPolicy.id
            this.updatedAt = updateTimestamp
            this.createdAt = updatedPolicy.info.createTime.toOffsetDateTime()
            this.active = true
            this.title = updatedPolicy.info.title
            this.description = updatedPolicy.info.description
            this.version = updatedPolicy.info.version
            this.organizationId = context
        }.also {
            it.store()
        }

        return updatedPolicy
    }

    private fun deactivateDataPolicy(title: String, context: String, version: String, updateTime: OffsetDateTime, dslContext: DSLContext = jooq) {
        dslContext.update(DATA_POLICIES)
            .set(DATA_POLICIES.ACTIVE, false)
            .where(DATA_POLICIES.TITLE.eq(title))
            .and(DATA_POLICIES.ORGANIZATION_ID.eq(context))
            .and(DATA_POLICIES.VERSION.eq(version))
            .and(DATA_POLICIES.UPDATED_AT.eq(updateTime))
            .execute()
    }

    private fun getDataPolicy(title: String, context: String, version: String, dslContext: DSLContext = jooq): DataPolicy? {
        return dslContext.select()
            .from(DATA_POLICIES)
            .where(DATA_POLICIES.TITLE.eq(title))
            .and(DATA_POLICIES.ORGANIZATION_ID.eq(context))
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
