package io.strmprivacy.management.data_policy_service.service

import DataPolicyDao
import InvalidDataPolicyEmptyFieldTransforms
import InvalidDataPolicyMissingAttribute
import InvalidDataPolicyNonEmptyLastFieldTransform
import InvalidDataPolicyOverlappingAttributes
import InvalidDataPolicyOverlappingPrincipals
import InvalidDataPolicyUnknownGroup
import ProcessingPlatformsService
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import coWithTransactionResult
import io.strmprivacy.api.account.v1.AccountServiceGrpcKt
import io.strmprivacy.api.account.v1.GetAccountDetailsRequest
import io.strmprivacy.grpc.common.auth.withCallerPrincipal
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import pathString

@Component
class DataPolicyService(
    private val dataPolicyDao: DataPolicyDao,
    private val accountService: AccountServiceGrpcKt.AccountServiceCoroutineStub,
    private val processingPlatforms: ProcessingPlatformsService,
    private val jooq: DSLContext,
) {
    suspend fun listDataPolicies(): List<DataPolicy> = dataPolicyDao.listDataPolicies(getOrganizationId())

    suspend fun upsertDataPolicy(dataPolicy: DataPolicy): DataPolicy {
        val organizationId = getOrganizationId()
        validate(dataPolicy)
        return jooq.coWithTransactionResult {
            val newDataPolicy = dataPolicyDao.upsertDataPolicy(dataPolicy, organizationId, it)
            enforceStatement(newDataPolicy)
            newDataPolicy
        }
    }

    suspend fun validate(dataPolicy: DataPolicy) {
        val platform = processingPlatforms.getProcessingPlatform(dataPolicy)
        val validGroups = platform.listGroups().map { it.name }.toSet()
        val validAttributes = dataPolicy.source.attributesList.map(DataPolicy.Attribute::pathString).toSet()

        // check that every principal exists in validGroups
        fun checkPrincipals(principals: List<String>) {
            (principals.toSet() - validGroups).let {
                if (it.isNotEmpty()) {
                    throw InvalidDataPolicyUnknownGroup(it)
                }
            }
        }

        // check that every attribute in a field transform or row filter exists in the DataPolicy
        fun checkAttribute(attribute: DataPolicy.Attribute) {
            if (!validAttributes.contains(attribute.pathString())) {
                throw InvalidDataPolicyMissingAttribute(attribute)
            }
        }

        dataPolicy.ruleSetsList.forEach { ruleSet ->
            ruleSet.fieldTransformsList.forEach { fieldTransform ->
                checkAttribute(fieldTransform.attribute)
                if (fieldTransform.transformsList.isEmpty()) {
                    throw InvalidDataPolicyEmptyFieldTransforms(fieldTransform)
                }
                fieldTransform.transformsList.forEach { transform ->
                    checkPrincipals(transform.principalsList)
                }
                fieldTransform.transformsList.last().let { transform ->
                    if (transform.principalsList.isNotEmpty()) {
                        throw InvalidDataPolicyNonEmptyLastFieldTransform(transform)
                    }
                }
                // check non-overlapping principals within one fieldTransform
                fieldTransform.transformsList.fold(
                    emptySet<String>(),
                ) { alreadySeen, transform ->
                    transform.principalsList.toSet().let {
                        if (alreadySeen.intersect(it).isNotEmpty()) {
                            throw InvalidDataPolicyOverlappingPrincipals(fieldTransform)
                        }
                        alreadySeen + it
                    }
                }
            }
            // check for every row filter that the principals overlap with groups in the processing platform
            // and that the attributes exist in the DataPolicy
            ruleSet.rowFiltersList.forEach { rowFilter ->
                checkAttribute(rowFilter.attribute)
                rowFilter.conditionsList.forEach { condition ->
                    checkPrincipals(condition.principalsList)
                }
            }
            // check non-overlapping attributes within one ruleset
            ruleSet.fieldTransformsList.map { it.attribute }.fold(
                emptySet<String>(),
            ) { alreadySeen, attribute ->
                attribute.pathString().let {
                    if (alreadySeen.contains(it)) {
                        throw InvalidDataPolicyOverlappingAttributes(ruleSet)
                    }
                    alreadySeen + it
                }
            }
        }
    }

    fun getLatestDataPolicy(id: String) = dataPolicyDao.getLatestDataPolicy(id)

    private suspend fun getOrganizationId(): String = withCallerPrincipal { metadata ->
        accountService.getAccountDetails(GetAccountDetailsRequest.getDefaultInstance(), metadata)
    }.organizationId

    private suspend fun enforceStatement(dataPolicy: DataPolicy) {
        // TODO: replace with switch based on platform identifier instead of type. Possibly multiple platforms of the same type.
        val platform = processingPlatforms.getProcessingPlatform(dataPolicy)
        platform.applyPolicy(dataPolicy)
    }
}
