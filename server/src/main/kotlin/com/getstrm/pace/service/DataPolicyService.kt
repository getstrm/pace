package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ListDataPoliciesRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.TranspileDataPolicyRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.UpsertDataPolicyRequest
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import com.getstrm.jooq.generated.tables.records.DataPoliciesRecord
import com.getstrm.pace.config.ProcessingPlatformsConfiguration
import com.getstrm.pace.dao.DataPolicyDao
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.MILLION_RECORDS
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.orDefault
import com.getstrm.pace.util.sourceDataResourceRef
import com.getstrm.pace.util.targetDataResourceRefs
import com.getstrm.pace.util.toApiDataPolicy
import com.google.rpc.BadRequest
import com.google.rpc.ResourceInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DataPolicyService(
    private val dataPolicyDao: DataPolicyDao,
    private val processingPlatforms: ProcessingPlatformsService,
    private val config: ProcessingPlatformsConfiguration,
    private val dataPolicyValidator: DataPolicyValidator,
) {
    suspend fun listDataPolicies(request: ListDataPoliciesRequest): PagedCollection<DataPolicy> =
        dataPolicyDao.listDataPolicies(request.pageParameters.orDefault()).map {
            it.toApiDataPolicy()
        }

    @Transactional
    suspend fun upsertDataPolicy(request: UpsertDataPolicyRequest): DataPolicy {
        dataPolicyValidator.validate(
            request.dataPolicy,
            processingPlatforms.listGroupNames(request.dataPolicy.source.ref.platform.id)
        ) {
            skipCheckPrincipals =
                config.bigquery
                    .firstOrNull { it.id == request.dataPolicy.source.ref.platform?.id }
                    ?.useIamCheckExtension ?: false
        }

        return dataPolicyDao
            .upsertDataPolicy(request.dataPolicy)
            .also { if (request.apply) applyDataPolicy(it) }
            .toApiDataPolicy()
    }

    suspend fun applyDataPolicy(id: String, platformId: String): DataPolicy =
        applyDataPolicy(getActiveDataPolicy(id, platformId))

    suspend fun applyDataPolicy(dataPolicy: DataPoliciesRecord): DataPolicy {
        val latestPolicy = dataPolicy.toApiDataPolicy()
        processingPlatforms.getProcessingPlatform(latestPolicy).applyPolicy(latestPolicy)
        return dataPolicyDao.applyDataPolicy(dataPolicy).toApiDataPolicy()
    }

    suspend fun transpileDataPolicy(request: TranspileDataPolicyRequest): String {
        val dataPolicy =
            when (request.dataPolicyCase) {
                TranspileDataPolicyRequest.DataPolicyCase.INLINE_DATA_POLICY ->
                    request.inlineDataPolicy
                TranspileDataPolicyRequest.DataPolicyCase.DATA_POLICY_REF ->
                    getLatestDataPolicy(
                        request.dataPolicyRef.dataPolicyId,
                        request.dataPolicyRef.platformId
                    )
                TranspileDataPolicyRequest.DataPolicyCase.DATAPOLICY_NOT_SET,
                null ->
                    throw BadRequestException(
                        BadRequestException.Code.INVALID_ARGUMENT,
                        BadRequest.newBuilder()
                            .addAllFieldViolations(
                                listOf(
                                    BadRequest.FieldViolation.newBuilder()
                                        .setField("data_policy")
                                        .setDescription("data_policy must be set")
                                        .build()
                                )
                            )
                            .build()
                    )
            }

        return processingPlatforms
            .getProcessingPlatform(dataPolicy)
            .transpilePolicy(dataPolicy, true)
    }

    fun getLatestDataPolicy(id: String, platformId: String): DataPolicy =
        getActiveDataPolicy(id, platformId).toApiDataPolicy()

    suspend fun listAllManagedDataResourceRefs(): List<ResourceUrn> {
        val dataPolicies =
            listDataPolicies(
                    ListDataPoliciesRequest.newBuilder().setPageParameters(MILLION_RECORDS).build()
                )
                .data
        return dataPolicies.flatMap {
            listOf(it.sourceDataResourceRef()) + it.targetDataResourceRefs()
        }
    }

    private fun getActiveDataPolicy(id: String, platformId: String): DataPoliciesRecord =
        dataPolicyDao.getActiveDataPolicy(id, platformId)
            ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("DataPolicy")
                    .setResourceName(id)
                    .setDescription("DataPolicy $id not found")
                    .build()
            )
}
