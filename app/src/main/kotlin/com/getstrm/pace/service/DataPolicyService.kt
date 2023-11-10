package com.getstrm.pace.service

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
    suspend fun upsertDataPolicy(dataPolicy: DataPolicy): DataPolicy {
        dataPolicyValidatorService.validate(dataPolicy, processingPlatforms.listGroupNames(dataPolicy.platform.id))
        val newDataPolicy = dataPolicyDao.upsertDataPolicy(dataPolicy)
        processingPlatforms.getProcessingPlatform(newDataPolicy).applyPolicy(newDataPolicy)
        return newDataPolicy
    }

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
