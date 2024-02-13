package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ListDataPoliciesRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ScanLineageRequest
import build.buf.gen.getstrm.pace.api.entities.v1alpha.LineageSummary
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.util.PagedCollection
import org.springframework.stereotype.Component

@Component
class LineageService(
    private val dataPolicyService: DataPolicyService,
    private val processingPlatformsService: ProcessingPlatformsService
) {

    suspend fun getLineage(request: GetLineageRequest): LineageSummary =
        determineIfManagedByPace(processingPlatformsService.getLineage(request))

    suspend fun scanLineage(request: ScanLineageRequest): PagedCollection<LineageSummary> {
        val policies =
            dataPolicyService.listDataPolicies(
                ListDataPoliciesRequest.newBuilder()
                    .setPageParameters(request.pageParameters)
                    .build()
            )

        return policies.coMap { policy ->
            determineIfManagedByPace(
                try {
                    processingPlatformsService.getLineage(
                        GetLineageRequest.newBuilder()
                            .setFqn(policy.source.ref.integrationFqn)
                            .setPlatformId(policy.source.ref.platform.id)
                            .build()
                    )
                } catch (e: InternalException) {
                    // FIXME implement other platforms than BigQuery
                    LineageSummary.getDefaultInstance()
                }
            )
        }
    }

    suspend fun determineIfManagedByPace(summary: LineageSummary): LineageSummary =
        with(summary.toBuilder()) {
            downstreamBuilderList.forEach { it.setManagedByPace(managedByPace(it.resourceRef)) }
            upstreamBuilderList.forEach { it.setManagedByPace(managedByPace(it.resourceRef)) }
            build()
        }

    /**
     * return true if a Data Resource is managed by pace.
     *
     * so either a data-resource has a data-policy connected to it, or one of the ruleset targets is
     * equal to ref.
     *
     * TODO improve stupid algorithm, performance is abysmal. But good enough for now.
     */
    private suspend fun managedByPace(ref: ResourceUrn): Boolean =
        dataPolicyService.listAllManagedDataResourceRefs().firstOrNull { it == ref } != null
}
