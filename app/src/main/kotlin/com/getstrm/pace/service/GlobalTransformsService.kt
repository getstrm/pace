package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.RefAndType
import com.getstrm.pace.dao.GlobalTransformsDao
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.name
import com.getstrm.pace.util.toGlobalTransform
import com.google.rpc.ResourceInfo
import org.springframework.stereotype.Component
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform as ApiFieldTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform as ApiTransform

@Component
class GlobalTransformsService(
    private val dataPolicyService: DataPolicyService,
    private val globalTransformsDao: GlobalTransformsDao,
) {

    fun getFieldTransforms(refAndType: RefAndType): GlobalTransform {
        val record = globalTransformsDao.getTransform(refAndType) ?: throw ResourceException(
            ResourceException.Code.NOT_FOUND,
            ResourceInfo.newBuilder()
                .setResourceName(refAndType.name())
                .setResourceType("GlobalTransform ${refAndType.type}")
                .build()
        )

        return record.toGlobalTransform()
    }

    fun getFieldTransformOrNull(refAndType: RefAndType): GlobalTransform? =
        globalTransformsDao.getTransform(refAndType)?.toGlobalTransform()


    /**
     * add a rule set to a data policy based on tags.
     *
     * @param dataPolicy bare DataPolicy
     * @return policy with embedded ruleset.
     */
    suspend fun addRuleSet(dataPolicy: DataPolicy): DataPolicy {
        val fieldTransforms = dataPolicy.source.fieldsList.filter { it.tagsList.isNotEmpty() }.map { field ->

            with(ApiFieldTransform.newBuilder()) {
                this.field = field
                this.addAllTransforms(
                    field.tagsList.flatMap { tag ->
                        // TODO this should be a batch call for multiple refAndTypes
                        val tagTransform = getFieldTransformOrNull(
                            RefAndType.newBuilder()
                                .setRef(tag)
                                .setType(GlobalTransform.TransformCase.TAG_TRANSFORM.name)
                                .build()
                        )?.tagTransform

                        tagTransform?.transformsList ?: emptyList()
                    }.filterFieldTransforms(),
                )
            }.build()
        }
        val policyWithRuleSet = dataPolicy.toBuilder()
            .addRuleSets(RuleSet.newBuilder().addAllFieldTransforms(fieldTransforms))
            .build()
        dataPolicyService.validate(policyWithRuleSet)
        return policyWithRuleSet
    }
}

/** enforce non-overlapping principals on the ApiTransforms in one FieldTransform.
 * First one wins.
 *
 * Since each tag can have a list of associated ApiTransforms, this makes the
 * ORDER of tags important. Let's hope the catalogs present the tags in a deterministic order!
 *
 * a certain fields the tags define non-overlapping rules? The [DataPolicyService.validate]
 * method already executes this check.
 *
 * The strategy here is an ongoing discussion: https://github.com/getstrm/pace/issues/33
 */
fun List<ApiTransform>.filterFieldTransforms(): List<ApiTransform> {
    val filtered: List<ApiTransform> = this.fold(
        emptySet<String>() to listOf<ApiTransform>(),
    ) {
            (
                /* the principals that we've already encountered while going through the list */
                alreadySeenPrincipals: Set<String>,
                /* the cleaned-up list of ApiTransforms */
                acc: List<ApiTransform>,
            ),
            /* the original ApiTransform */
            transform: ApiTransform,
        ->
        // TODO principals should also support other types than just groups
        val principals = transform.principalsList.map { it.group }.toSet() - alreadySeenPrincipals
        val dataPolicyWithoutOverlappingPrincipals = transform.toBuilder()
            .clearPrincipals()
            .addAllPrincipals(principals.map { DataPolicy.Principal.newBuilder().setGroup(it).build() })
            .build()
        alreadySeenPrincipals + principals to
            acc + dataPolicyWithoutOverlappingPrincipals
    }.second
    // now remove duplicate defaults (without principals
    val (defaults, withPrincipals) = filtered.partition { it.principalsCount == 0 }
    return (withPrincipals + defaults.firstOrNull()).filterNotNull()
}
