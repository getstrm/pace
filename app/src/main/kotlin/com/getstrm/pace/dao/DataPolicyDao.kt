package com.getstrm.pace.dao

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.jooq.generated.tables.DataPolicies.Companion.DATA_POLICIES
import com.getstrm.jooq.generated.tables.records.DataPoliciesRecord
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.util.toJsonb
import com.getstrm.pace.util.toOffsetDateTime
import com.getstrm.pace.util.toTimestamp
import com.google.protobuf.extensionRangeOptions
import com.google.protobuf.util.JsonFormat
import com.google.rpc.BadRequest
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

// Todo: tidy up and add tests.
@Component
class DataPolicyDao(
    private val jooq: DSLContext,
) {

    fun listDataPolicies(): List<DataPolicy> = jooq.select()
        .from(DATA_POLICIES)
        .where(DATA_POLICIES.ACTIVE.isTrue)
        .fetchInto(DATA_POLICIES).map { it.toApiDataPolicy() }

    fun upsertDataPolicy(dataPolicy: DataPolicy): DataPolicy {
        val id = dataPolicy.id.ifBlank { dataPolicy.source.ref }
        val oldPolicy = getActiveDataPolicy(id)?.also { existingPolicy ->
            checkVersionChanged(existingPolicy.version.orEmpty(), dataPolicy.metadata.version)
            deactivateDataPolicy(existingPolicy)
        }
        val updateTimestamp = OffsetDateTime.now()
        val updatedPolicy = dataPolicy.toBuilder()
            .setId(id)
            .setMetadata(
                dataPolicy.metadata.toBuilder().setUpdateTime(updateTimestamp.toTimestamp())
                    .setCreateTime((oldPolicy?.createdAt ?: updateTimestamp).toTimestamp()).build()
            ).build()

        jooq.newRecord(DATA_POLICIES).apply {
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

    fun getLatestDataPolicy(id: String): DataPolicy? = getActiveDataPolicy(id)?.toApiDataPolicy()

    private fun getActiveDataPolicy(id: String): DataPoliciesRecord? = jooq.select()
        .from(DATA_POLICIES)
        .where(DATA_POLICIES.ID.eq(id))
        .and(DATA_POLICIES.ACTIVE.isTrue)
        .limit(1)
        .fetchOneInto(DATA_POLICIES)

    // Todo: improve, this doesn't check that the "new" version is actually newer or hasn't been used before.
    private fun checkVersionChanged(
        existingVersion: String,
        newVersion: String,
    ) {
        if (existingVersion == newVersion) {
            throw BadRequestException(
                BadRequestException.Code.INVALID_ARGUMENT,
                BadRequest.newBuilder()
                    .addAllFieldViolations(
                        listOf(
                            BadRequest.FieldViolation.newBuilder()
                                .setField("metadata.version")
                                .setDescription("DataPolicy version should be different from the existing version '${existingVersion}'.")
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

fun DataPoliciesRecord?.toApiDataPolicy(): DataPolicy? = this?.policy?.let {
    with(DataPolicy.newBuilder()) {
        JsonFormat.parser().ignoringUnknownFields().merge(it.data(), this)
        build()
    }
}
