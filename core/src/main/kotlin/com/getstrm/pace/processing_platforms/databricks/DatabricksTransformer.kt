package com.getstrm.pace.processing_platforms.databricks

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.throwUnimplemented
import com.getstrm.pace.processing_platforms.ProcessingPlatformRenderer
import com.getstrm.pace.processing_platforms.ProcessingPlatformTransformer
import com.getstrm.pace.util.defaultJooqSettings
import com.getstrm.pace.util.fullName
import com.getstrm.pace.util.toJooqField
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL

object DatabricksTransformer : ProcessingPlatformTransformer(DatabricksRenderer) {
    override fun hash(
        field: DataPolicy.Field,
        hash: DataPolicy.RuleSet.FieldTransform.Transform.Hash
    ): Field<*> {
        val dataType = field.toJooqField().dataType
        return if (dataType.isNumeric) {
            DSL.field(
                "hash({0})",
                String::class.java,
                DSL.unquotedName(field.fullName()),
            )
        } else if (dataType.isString) {
            DSL.field(
                "sha2(cast({0} as string), 256)",
                String::class.java,
                DSL.unquotedName(field.fullName()),
            )
        } else {
            throwUnimplemented("Hashing a ${dataType.typeName} type")
        }
    }

    private object DatabricksRenderer : ProcessingPlatformRenderer {

        /**
         * BigQuery requires backticked names for certain names, and MySQL dialect uses backticks,
         * so we use this here.
         */
        private val dslContext = DSL.using(SQLDialect.DEFAULT, defaultJooqSettings())

        override fun renderName(name: String): String =
            dslContext.renderNamedParams(DSL.unquotedName(name))
    }
}
