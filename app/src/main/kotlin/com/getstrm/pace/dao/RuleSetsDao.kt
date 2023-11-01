package com.getstrm.pace.dao

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import org.springframework.stereotype.Component

@Component
class RuleSetsDao {
    suspend fun getFieldTransforms(tag: String): List<DataPolicy.RuleSet.FieldTransform.Transform> {
        // TODO needs DAO
        TODO()
    }

    suspend fun getFilters(tag: String): List<DataPolicy.RuleSet.Filter> {
        // TODO needs DAO
        TODO()
    }
}
