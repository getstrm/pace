package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform as ApiFieldTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform as ApiTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.TransformCase
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

    /**
     * add a rule set to a data policy based on tags.
     *
     * @param dataPolicy blueprint DataPolicy
     * @return policy with embedded ruleset.
     */
    suspend fun addRuleSet(dataPolicy: DataPolicy): DataPolicy {
        val fieldTransforms =
            dataPolicy.source.fieldsList
                .filter { it.tagsList.isNotEmpty() }
                .mapNotNull { field ->
                    val transforms =
                        field.tagsList
                            .flatMap { tag ->
                                // TODO this should be a batch call for multiple refAndTypes
                                getTransformOrNull(tag, TransformCase.TAG_TRANSFORM)
                                    ?.tagTransform
                                    ?.transformsList ?: emptyList()
                            }
                            .combineTransforms()

                    return@mapNotNull if (transforms.isNotEmpty()) {
                        ApiFieldTransform.newBuilder()
                            .setField(field)
                            .addAllTransforms(transforms)
                            .build()
                    } else {
                        // Ensure no field transforms are added to the ruleset that don't have any
                        // transforms
                        null
                    }
                }

        return if (fieldTransforms.isNotEmpty()) {
            dataPolicy
                .toBuilder()
                .addRuleSets(
                    RuleSet.newBuilder()
                        .setTarget(
                            DataPolicy.Target.newBuilder()
                                .setFullname(
                                    "${dataPolicy.source.ref}${appConfiguration.defaultViewSuffix}"
                                )
                                .build()
                        )
                        .addAllFieldTransforms(fieldTransforms)
                )
                .build()
        } else {
            dataPolicy
        }
    }
}

/**
 * enforce non-overlapping principals on the ApiTransforms in one FieldTransform. First one wins.
 *
 * Since each tag can have a list of associated ApiTransforms, this makes the ORDER of tags
 * important. Let's hope the catalogs present the tags in a deterministic order!
 *
 * a certain fields the tags define non-overlapping rules? The [DataPolicyValidatorService.validate]
 * method already executes this check.
 *
 * The strategy here is an ongoing discussion: https://github.com/getstrm/pace/issues/33
 */
fun List<ApiTransform>.combineTransforms(): List<ApiTransform> {
    val filtered: List<ApiTransform> =
        this.fold(
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
                val principals =
                    transform.principalsList.map { it.group }.toSet() - alreadySeenPrincipals
                val dataPolicyWithoutOverlappingPrincipals =
                    transform
                        .toBuilder()
                        .clearPrincipals()
                        .addAllPrincipals(
                            principals.map {
                                DataPolicy.Principal.newBuilder().setGroup(it).build()
                            }
                        )
                        .build()
                alreadySeenPrincipals + principals to acc + dataPolicyWithoutOverlappingPrincipals
            }
            .second
    // now remove duplicate defaults (without principals
    val (defaults, withPrincipals) = filtered.partition { it.principalsCount == 0 }
    return (withPrincipals + defaults.firstOrNull()).filterNotNull()
}
