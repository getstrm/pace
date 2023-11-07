package com.getstrm.pace.api


import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.*
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
