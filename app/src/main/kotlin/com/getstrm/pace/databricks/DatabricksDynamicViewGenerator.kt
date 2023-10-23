package com.getstrm.pace.databricks

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.pace.common.AbstractDynamicViewGenerator
import com.getstrm.pace.common.Principal
import org.jooq.Condition
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class DatabricksDynamicViewGenerator(
    dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {}
) : AbstractDynamicViewGenerator(
    dataPolicy,
    customJooqSettings
) {
    override fun List<Principal>.toPrincipalCondition(): Condition? {
        return if (isEmpty()) {
            null
        } else {
            DSL.or(map { principal -> DSL.condition("is_account_group_member({0})", principal) })
        }
    }
}
