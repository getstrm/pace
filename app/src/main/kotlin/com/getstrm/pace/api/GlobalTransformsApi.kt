package com.getstrm.pace.api

import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.global_transforms.v1alpha.*
import com.getstrm.pace.service.GlobalTransformsService
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class GlobalTransformsApi(
    private val globalTransformsService: GlobalTransformsService
) : GlobalTransformsServiceGrpcKt.GlobalTransformsServiceCoroutineImplBase() {
    override suspend fun getGlobalTransform(request: GetGlobalTransformRequest): GetGlobalTransformResponse =
        GetGlobalTransformResponse.newBuilder()
            .setTransform(
                globalTransformsService.getTransform(
                    GlobalTransform.RefAndType.newBuilder()
                        .setRef(request.ref)
                        .setType(request.type)
                        .build()
                )
            )
            .build()

    override suspend fun listGlobalTransforms(request: ListGlobalTransformsRequest): ListGlobalTransformsResponse =
        ListGlobalTransformsResponse.newBuilder()
            .addAllGlobalTransforms(globalTransformsService.listTransforms())
            .build()

    override suspend fun upsertGlobalTransform(request: UpsertGlobalTransformRequest): UpsertGlobalTransformResponse =
        UpsertGlobalTransformResponse.newBuilder()
            .setTransform(globalTransformsService.upsertTransform(request.transform))
            .build()

    override suspend fun deleteGlobalTransform(request: DeleteGlobalTransformRequest): DeleteGlobalTransformResponse =
        DeleteGlobalTransformResponse.newBuilder()
            .setDeletedCount(globalTransformsService.deleteTransforms(request.refAndTypesList))
            .build()
}
