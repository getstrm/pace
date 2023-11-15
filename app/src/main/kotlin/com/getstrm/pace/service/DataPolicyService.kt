package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.UpsertDataPolicyRequest
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.dao.DataPolicyDao
import com.getstrm.pace.exceptions.ResourceException
import com.google.rpc.ResourceInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DataPolicyService(
    private val dataPolicyDao: DataPolicyDao,
    private val processingPlatforms: ProcessingPlatformsService,
    private val dataPolicyValidatorService: DataPolicyValidatorService
) {
    suspend fun listDataPolicies(): List<DataPolicy> = dataPolicyDao.listDataPolicies()

    @Transactional
    suspend fun upsertDataPolicy(request: UpsertDataPolicyRequest): DataPolicy {
        dataPolicyValidatorService.validate(
            request.dataPolicy,
            processingPlatforms.listGroupNames(request.dataPolicy.platform.id)
        )

        return dataPolicyDao.upsertDataPolicy(request.dataPolicy).also {
            if (request.apply) applyDataPolicy(it.id, it.platform.id)
        }
    }

    suspend fun applyDataPolicy(id: String, platformId: String): DataPolicy =
        getLatestDataPolicy(id, platformId)
            .also { processingPlatforms.getProcessingPlatform(it).applyPolicy(it) }

    fun getLatestDataPolicy(id: String, platformId: String): DataPolicy =
        dataPolicyDao.getLatestDataPolicy(id, platformId) ?: throw ResourceException(
            ResourceException.Code.NOT_FOUND,
            ResourceInfo.newBuilder()
                .setResourceType("DataPolicy")
                .setResourceName(id)
                .setDescription("DataPolicy $id not found")
                .build()
        )
}
