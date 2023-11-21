import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException
import com.getstrm.pace.processing_platforms.ProcessingPlatformTransformer
import com.getstrm.pace.util.fullName
import com.google.rpc.BadRequest
import com.google.rpc.DebugInfo
import org.jooq.Field
import org.jooq.impl.DSL

class SynapseTransformer : ProcessingPlatformTransformer {
    override fun regexpReplace(
        field: DataPolicy.Field,
        regexp: DataPolicy.RuleSet.FieldTransform.Transform.Regexp
    ): Field<*> {
        throw BadRequestException(
            BadRequestException.Code.INVALID_ARGUMENT,
            BadRequest.newBuilder()
                .addFieldViolations(
                    BadRequest.FieldViolation.newBuilder()
                        .setField("dataPolicy.ruleSetsList.fieldTransformsList.regexpReplace")
                        .setDescription("regex replace is not available for platform Synapse. ${PaceStatusException.UNIMPLEMENTED}")
                )
                .build()
        )
    }
    override fun hash(field: DataPolicy.Field, hash: DataPolicy.RuleSet.FieldTransform.Transform.Hash): Field<*> =
        DSL.field(
            "HASHBYTES('SHA2_512', {0})",
            Any::class.java,
            DSL.unquotedName(field.fullName()),
        )
}
