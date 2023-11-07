package com.getstrm.pace.dao

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import org.springframework.stereotype.Component

@Component
class RuleSetsDao {
    suspend fun getFieldTransforms(ref: String, transformType: GlobalTransform.TransformCase): GlobalTransform {
        // TODO needs DAO
        TODO()
    }

    suspend fun getFilters(tag: String): List<DataPolicy.RuleSet.Filter> {
        // TODO needs DAO
        TODO()
    }
}
