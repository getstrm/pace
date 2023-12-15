package com.getstrm.pace.dao

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.jooq.generated.tables.DataPolicies.Companion.DATA_POLICIES
import com.getstrm.jooq.generated.tables.records.DataPoliciesRecord
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.util.*
import com.google.rpc.BadRequest
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

// Todo: tidy up and add tests.
@Component
class DataPolicyDao(
    private val jooq: DSLContext,
) {

    fun listDataPolicies(pageParameters: PageParameters): PagedCollection<DataPoliciesRecord> =
        jooq.select()
        .from(DATA_POLICIES)
        .where(DATA_POLICIES.ACTIVE.isTrue)
        .orderBy(DATA_POLICIES.ID, DATA_POLICIES.PLATFORM_ID, DATA_POLICIES.VERSION)
        .offset(pageParameters.skip)
        .limit(pageParameters.pageSize)
        .fetchInto(DATA_POLICIES)
        .withUnknownTotals()

    fun upsertDataPolicy(dataPolicy: DataPolicy): DataPoliciesRecord {
        val id = dataPolicy.id.ifBlank { dataPolicy.source.ref }
        val oldPolicy = getActiveDataPolicy(id, dataPolicy.platform.id)?.also {
            checkStaleness(it.version!!, dataPolicy.metadata.version)
            deactivateDataPolicy(it)
        }
        val newVersion = oldPolicy?.version?.plus(1) ?: 1
        val updateTimestamp = OffsetDateTime.now()
        val updatedPolicy = dataPolicy.toBuilder()
            .setId(id)
            .setMetadata(
                dataPolicy.metadata.toBuilder()
                    .setVersion(newVersion)
                    .setUpdateTime(updateTimestamp.toTimestamp())
                    .setCreateTime((oldPolicy?.createdAt ?: updateTimestamp).toTimestamp())
            ).build()

        return jooq.newRecord(DATA_POLICIES).apply {
            this.policy = updatedPolicy.toJsonbWithDefaults()
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
    }

    fun applyDataPolicy(dataPolicy: DataPoliciesRecord): DataPoliciesRecord {
        val now = OffsetDateTime.now()
        val updatedApiDataPolicy = with(dataPolicy.toApiDataPolicy()) {
            toBuilder()
                .setMetadata(
                    metadata.toBuilder()
                        .setUpdateTime(now.toTimestamp())
                        .setLastApplyTime(now.toTimestamp())
                ).build()
        }
        return dataPolicy.apply {
            this.policy = updatedApiDataPolicy.toJsonbWithDefaults()
            this.updatedAt = now
        }.also {
            it.store()
        }
    }

    fun getActiveDataPolicy(id: String, platformId: String): DataPoliciesRecord? = jooq.select()
        .from(DATA_POLICIES)
        .where(DATA_POLICIES.ID.eq(id))
        .and(DATA_POLICIES.PLATFORM_ID.eq(platformId))
        .and(DATA_POLICIES.ACTIVE.isTrue)
        .limit(1)
        .fetchOneInto(DATA_POLICIES)

    private fun checkStaleness(existingVersion: Int, newVersion: Int) {
        if (existingVersion != newVersion) {
            throw BadRequestException(
                BadRequestException.Code.INVALID_ARGUMENT,
                BadRequest.newBuilder()
                    .addAllFieldViolations(
                        listOf(
                            BadRequest.FieldViolation.newBuilder()
                                .setField("metadata.version")
                                .setDescription("DataPolicy version '$newVersion' is not the latest version '$existingVersion'. Please make sure your update is based on the latest version.")
                                .build()
                        )
                    )
                    .build()
            )
        }
    }

    private fun deactivateDataPolicy(dataPolicy: DataPoliciesRecord) {
        jooq.update(DATA_POLICIES)
            .set(DATA_POLICIES.ACTIVE, false)
            .where(DATA_POLICIES.ID.eq(dataPolicy.id))
            .and(DATA_POLICIES.VERSION.eq(dataPolicy.version))
            .execute()
    }
}
