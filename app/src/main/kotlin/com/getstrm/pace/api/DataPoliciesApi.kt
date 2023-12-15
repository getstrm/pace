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
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.UpsertDataPolicyRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.UpsertDataPolicyResponse
import com.getstrm.pace.service.DataPolicyEvaluationService
import com.getstrm.pace.service.DataPolicyService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class DataPoliciesApi(
    private val dataPolicyService: DataPolicyService,
    private val dataPolicyEvaluationService: DataPolicyEvaluationService,
) : DataPoliciesServiceGrpcKt.DataPoliciesServiceCoroutineImplBase() {
    override suspend fun listDataPolicies(request: ListDataPoliciesRequest): ListDataPoliciesResponse {
        val (policies, pageInfo) = dataPolicyService.listDataPolicies(request)
        return ListDataPoliciesResponse.newBuilder()
            .addAllDataPolicies(policies)
            .setPageInfo(pageInfo)
            .build()
    }

    override suspend fun upsertDataPolicy(request: UpsertDataPolicyRequest): UpsertDataPolicyResponse {
        return UpsertDataPolicyResponse.newBuilder()
            .setDataPolicy(dataPolicyService.upsertDataPolicy(request))
            .build()
    }

    override suspend fun evaluateDataPolicy(request: EvaluateDataPolicyRequest): EvaluateDataPolicyResponse {
        val policy = dataPolicyService.getLatestDataPolicy(request.dataPolicyId, request.platformId)
        return EvaluateDataPolicyResponse.newBuilder()
            .setFullEvaluationResult(
                dataPolicyEvaluationService.evaluatePolicy(policy, request.fullEvaluation.sampleCsv)
            )
            .build()
    }

    override suspend fun applyDataPolicy(request: ApplyDataPolicyRequest): ApplyDataPolicyResponse {
        return ApplyDataPolicyResponse.newBuilder()
            .setDataPolicy(dataPolicyService.applyDataPolicy(request.dataPolicyId, request.platformId))
            .build()
    }

    override suspend fun getDataPolicy(request: GetDataPolicyRequest): GetDataPolicyResponse {
        return GetDataPolicyResponse.newBuilder()
            .setDataPolicy(dataPolicyService.getLatestDataPolicy(request.dataPolicyId, request.platformId))
            .build()
    }
}
