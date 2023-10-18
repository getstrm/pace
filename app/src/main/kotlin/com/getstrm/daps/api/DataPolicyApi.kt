package com.getstrm.daps.api

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicyServiceGrpcKt
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformsRequest
import build.buf.gen.getstrm.api.data_policies.v1alpha.ListProcessingPlatformsResponse
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class DataPolicyApi : DataPolicyServiceGrpcKt.DataPolicyServiceCoroutineImplBase() {

    override suspend fun listProcessingPlatforms(request: ListProcessingPlatformsRequest): ListProcessingPlatformsResponse {
        return ListProcessingPlatformsResponse.newBuilder()
            .addProcessingPlatforms(
                DataPolicy.ProcessingPlatform.newBuilder()
                    .setId("test")
                    .setPlatformType(DataPolicy.ProcessingPlatform.PlatformType.SNOWFLAKE)
            )
            .build()
    }
}
