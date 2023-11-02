package com.getstrm.pace.api


import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.DataPoliciesServiceGrpcKt
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.GetDataPolicyRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.GetDataPolicyResponse
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ListDataPoliciesRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.ListDataPoliciesResponse
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.UpsertDataPolicyRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.UpsertDataPolicyResponse
import com.getstrm.pace.service.DataPolicyService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class DataPoliciesApi(
    private val dataPolicyService: DataPolicyService,
) : DataPoliciesServiceGrpcKt.DataPoliciesServiceCoroutineImplBase() {

    override suspend fun listDataPolicies(request: ListDataPoliciesRequest): ListDataPoliciesResponse {
        return ListDataPoliciesResponse.newBuilder()
            .addAllDataPolicies(dataPolicyService.listDataPolicies())
            .build()
    }

    override suspend fun upsertDataPolicy(request: UpsertDataPolicyRequest): UpsertDataPolicyResponse {
        return UpsertDataPolicyResponse.newBuilder()
            .setDataPolicy(dataPolicyService.upsertDataPolicy(request.dataPolicy))
            .build()
    }

    override suspend fun getDataPolicy(request: GetDataPolicyRequest): GetDataPolicyResponse {
        return GetDataPolicyResponse.newBuilder()
            .setDataPolicy(dataPolicyService.getLatestDataPolicy(request.dataPolicyId, request.platformId))
            .build()
    }
}
