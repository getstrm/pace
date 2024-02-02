package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform as ApiFieldTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform as ApiTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.TransformCase
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import com.getstrm.pace.config.AppConfiguration
import com.getstrm.pace.dao.GlobalTransformsDao
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.toGlobalTransform
import com.google.rpc.ResourceInfo
import org.springframework.stereotype.Component

@Component
class GlobalTransformsService(
    private val appConfiguration: AppConfiguration,
    private val globalTransformsDao: GlobalTransformsDao,
) {

    fun getTransform(ref: String, type: TransformCase): GlobalTransform {
        val record =
            globalTransformsDao.getTransform(ref, type)
                ?: throw ResourceException(
                    ResourceException.Code.NOT_FOUND,
                    ResourceInfo.newBuilder()
                        .setResourceName(ref)
                        .setResourceType(type.toString())
                        .build()
                )

        return record.toGlobalTransform()
    }

    fun getTransformOrNull(ref: String, type: TransformCase): GlobalTransform? =
        globalTransformsDao.getTransform(ref, type)?.toGlobalTransform()

    // TODO add paging ?
    fun listTransforms(type: TransformCase? = null) =
        globalTransformsDao.listTransforms(type).map { it.toGlobalTransform() }

    fun upsertTransform(globalTransform: GlobalTransform): GlobalTransform =
        globalTransformsDao.upsertTransform(globalTransform).toGlobalTransform()

    fun deleteTransform(ref: String, type: TransformCase): Int =
        globalTransformsDao.deleteTransform(ref, type)
}
