package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ListDataPoliciesRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ScanLineageRequest
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataResourceRef
import build.buf.gen.getstrm.pace.api.entities.v1alpha.LineageSummary
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageResponse
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.withPageInfo
import org.springframework.stereotype.Component

@Component
class LineageService(
    private val dataPolicyService: DataPolicyService,
    private val processingPlatformsService: ProcessingPlatformsService
) {

    suspend fun getLineage(request: GetLineageRequest): GetLineageResponse {
        val lineage = processingPlatformsService.getLineage(request)
        val withDataPolicyInfo = determineLineageDataPolicies(lineage.lineageSummary)
        val enriched =
            with(lineage.toBuilder()) {
                lineageSummary = withDataPolicyInfo
                build()
            }
        return enriched
    }

    suspend fun scanLineage(request: ScanLineageRequest): PagedCollection<LineageSummary> {
        val policies =
            dataPolicyService.listDataPolicies(
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
                        processingPlatformsService
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
            dataPolicyService.getLatestDataPolicy(ref.fqn, ref.platform.id)
            false
        } catch (e: ResourceException) {
            if (e.code == ResourceException.Code.NOT_FOUND) true else throw e
        }
}
