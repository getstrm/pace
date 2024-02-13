package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ApplyDataPolicyRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ApplyDataPolicyResponse
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.DataPoliciesServiceGrpcKt
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.EvaluateDataPolicyRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.EvaluateDataPolicyResponse
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.GetDataPolicyRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.GetDataPolicyResponse
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ListDataPoliciesRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ListDataPoliciesResponse
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ScanLineageRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ScanLineageResponse
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.TranspileDataPolicyRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.TranspileDataPolicyResponse
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.UpsertDataPolicyRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.UpsertDataPolicyResponse
import com.getstrm.pace.service.DataPolicyEvaluationService
import com.getstrm.pace.service.DataPolicyService
import com.getstrm.pace.service.LineageService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class DataPoliciesApi(
    private val dataPolicyService: DataPolicyService,
    private val dataPolicyEvaluationService: DataPolicyEvaluationService,
    private val lineageService: LineageService,
) : DataPoliciesServiceGrpcKt.DataPoliciesServiceCoroutineImplBase() {
    override suspend fun listDataPolicies(
        request: ListDataPoliciesRequest
    ): ListDataPoliciesResponse {
        val (policies, pageInfo) = dataPolicyService.listDataPolicies(request)
        return ListDataPoliciesResponse.newBuilder()
            .addAllDataPolicies(policies)
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun upsertDataPolicy(
        request: UpsertDataPolicyRequest
    ): UpsertDataPolicyResponse {
        return UpsertDataPolicyResponse.newBuilder()
            .setDataPolicy(dataPolicyService.upsertDataPolicy(request))
            .build()
    }

    override suspend fun evaluateDataPolicy(
        request: EvaluateDataPolicyRequest
    ): EvaluateDataPolicyResponse {
        return EvaluateDataPolicyResponse.newBuilder()
            .addAllRuleSetResults(dataPolicyEvaluationService.evaluate(request))
            .build()
    }

    override suspend fun applyDataPolicy(request: ApplyDataPolicyRequest): ApplyDataPolicyResponse {
        return ApplyDataPolicyResponse.newBuilder()
            .setDataPolicy(
                dataPolicyService.applyDataPolicy(request.dataPolicyId, request.platformId)
            )
            .build()
    }

    override suspend fun getDataPolicy(request: GetDataPolicyRequest): GetDataPolicyResponse {
        return GetDataPolicyResponse.newBuilder()
            .setDataPolicy(
                dataPolicyService.getLatestDataPolicy(request.dataPolicyId, request.platformId)
            )
            .build()
    }

    override suspend fun scanLineage(request: ScanLineageRequest): ScanLineageResponse {
        val lineage = lineageService.scanLineage(request)
        return ScanLineageResponse.newBuilder()
            .addAllLineageSummaries(lineage.data)
            .setPageInfo(lineage.pageInfo)
            .build()
    }

    override suspend fun transpileDataPolicy(
        request: TranspileDataPolicyRequest
    ): TranspileDataPolicyResponse {
        return TranspileDataPolicyResponse.newBuilder()
            .setSql(dataPolicyService.transpileDataPolicy(request))
            .build()
    }
}
