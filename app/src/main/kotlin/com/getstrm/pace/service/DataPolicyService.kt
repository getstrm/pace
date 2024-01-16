package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ListDataPoliciesRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ScanLineageRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.UpsertDataPolicyRequest
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataResourceRef
import build.buf.gen.getstrm.pace.api.entities.v1alpha.LineageSummary
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import com.getstrm.jooq.generated.tables.records.DataPoliciesRecord
import com.getstrm.pace.dao.DataPolicyDao
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.orDefault
import com.getstrm.pace.util.toApiDataPolicy
import com.getstrm.pace.util.withPageInfo
import com.google.rpc.ResourceInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DataPolicyService(
    private val dataPolicyDao: DataPolicyDao,
    private val processingPlatforms: ProcessingPlatformsService,
    private val dataPolicyValidatorService: DataPolicyValidatorService
) {
    suspend fun listDataPolicies(request: ListDataPoliciesRequest): PagedCollection<DataPolicy> =
        dataPolicyDao.listDataPolicies(request.pageParameters.orDefault()).map {
            it.toApiDataPolicy()
        }

    @Transactional
    suspend fun upsertDataPolicy(request: UpsertDataPolicyRequest): DataPolicy {
        dataPolicyValidatorService.validate(
            request.dataPolicy,
            processingPlatforms.listGroupNames(request.dataPolicy.platform.id)
        )

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

    fun getLatestDataPolicy(id: String, platformId: String): DataPolicy =
        getActiveDataPolicy(id, platformId).toApiDataPolicy()

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

    suspend fun scanLineage(request: ScanLineageRequest): PagedCollection<LineageSummary> {
        val policies =
            listDataPolicies(
                ListDataPoliciesRequest.newBuilder()
                    .setPageParameters(request.pageParameters)
                    .build()
            )

        // policies.map triggers this error
        // https://stackoverflow.com/questions/57329658/getting-suspension-functions-can-be-called-only-within-coroutine-body-when-cal
        // and I can't figure out to fix the utility function PagedCollection.map
        val lineageSummaries =
            policies.data.map { policy ->
                determineLineageDataPolicies(
                    try {
                        processingPlatforms
                            .getLineage(
                                GetLineageRequest.newBuilder()
                                    .setFqn(policy.source.ref)
                                    .setPlatformId(policy.platform.id)
                                    .build()
                            )
                            .lineageSummary
                    } catch (e: InternalException) {
                        // FIXME implement other platforms than BigQuery
                        LineageSummary.getDefaultInstance()
                    }
                )
            }
        return lineageSummaries.withPageInfo(policies.pageInfo)
    }

    suspend fun determineLineageDataPolicies(summary: LineageSummary): LineageSummary {
        return with(summary.toBuilder()) {
            downstreamBuilderList.forEach {
                it.setNotManagedByPace(notManagedByPace(it.resourceRef))
            }
            upstreamBuilderList.forEach { it.setNotManagedByPace(notManagedByPace(it.resourceRef)) }
            build()
        }
    }

    /**
     * return true if a Data Resource is not managed by pace.
     *
     * FIXME https://github.com/getstrm/pace/issues/153 Should include views created by pace
     */
    private suspend fun notManagedByPace(ref: DataResourceRef) =
        try {
            getLatestDataPolicy(ref.fqn, ref.platform.id)
            false
        } catch (e: ResourceException) {
            if (e.code == ResourceException.Code.NOT_FOUND) true else throw e
        }
}
