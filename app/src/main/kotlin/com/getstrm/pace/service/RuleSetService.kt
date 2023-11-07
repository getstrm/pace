package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import com.getstrm.pace.dao.RuleSetsDao
import org.springframework.stereotype.Component
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform as ApiFieldTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform as ApiTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter as ApiFilter

@Component
class RuleSetService(
    private val dataPolicyService: DataPolicyService,
    private val ruleSetsDao: RuleSetsDao,
) {

    private suspend fun getFieldTransforms(tag: String): GlobalTransform =
        ruleSetsDao.getFieldTransforms(tag, GlobalTransform.TransformCase.TAG_TRANSFORM)

    suspend fun getFilters(tag: String): List<ApiFilter> = ruleSetsDao.getFilters(tag)

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
                    field.tagsList.flatMap {
                        (getFieldTransforms(it).tagTransform).transformsList
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
 * TODO: think about if this is a good idea, or should we enforce that for
 * a certain fields the tags define non-overlapping rules? The [DataPolicyService.validate]
 * method already executes this check.
 *
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
