package com.getstrm.pace.service

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.pace.dao.DataPolicyDao
import com.getstrm.pace.domain.*
import com.getstrm.pace.exceptions.BadRequestException
import com.google.rpc.BadRequest
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import pathString
import toJson

@Component
class DataPolicyService(
    @Value("\${app.context}") private val context: String,
    private val dataPolicyDao: DataPolicyDao,
    private val processingPlatforms: ProcessingPlatformsService,
    private val jooq: DSLContext,
) {
    suspend fun listDataPolicies(): List<DataPolicy> = dataPolicyDao.listDataPolicies(context)

    suspend fun upsertDataPolicy(dataPolicy: DataPolicy): DataPolicy {
        validate(dataPolicy)
        // TODO should it remove old ruleset targets?
        // TODO the two statements below should be wrapped in a transaction
        val newDataPolicy = dataPolicyDao.upsertDataPolicy(dataPolicy, context, jooq)
        enforceStatement(newDataPolicy)
        return newDataPolicy
    }

    suspend fun validate(dataPolicy: DataPolicy) {
        if (dataPolicy.source.ref.isNullOrEmpty()) {
            throw BadRequestException(
                BadRequestException.Code.INVALID_ARGUMENT,
                BadRequest.newBuilder()
                    .addAllFieldViolations(
                        listOf(
                            BadRequest.FieldViolation.newBuilder()
                                .setField("dataPolicy.source.ref")
                                .setDescription("DataPolicy source ref is empty")
                                .build()
                        )
                    )
                    .build()
            )
        }
        if (dataPolicy.platform.id.isNullOrEmpty()) {
            throw BadRequestException(
                BadRequestException.Code.INVALID_ARGUMENT,
                BadRequest.newBuilder()
                    .addAllFieldViolations(
                        listOf(
                            BadRequest.FieldViolation.newBuilder()
                                .setField("dataPolicy.platform.id")
                                .setDescription("DataPolicy platform id is empty")
                                .build()
                        )
                    )
                    .build()
            )
        }
        val platform = processingPlatforms.getProcessingPlatform(dataPolicy)
        val validGroups = platform.listGroups().map { it.name }.toSet()
        val validAttributes = dataPolicy.source.attributesList.map(DataPolicy.Attribute::pathString).toSet()

        // check that every principal exists in validGroups
        fun checkPrincipals(principals: List<String>) {
            (principals.toSet() - validGroups).let {
                if (it.isNotEmpty()) {
                    throw BadRequestException(
                        BadRequestException.Code.INVALID_ARGUMENT,
                        BadRequest.newBuilder()
                            .addAllFieldViolations(
                                it.map { principal ->
                                    BadRequest.FieldViolation.newBuilder()
                                        .setField("principal")
                                        .setDescription("Principal $principal does not exist in platform ${dataPolicy.platform.id}")
                                        .build()
                                }
                            )
                            .build()
                    )
                }
            }
        }

        // check that every attribute in a field transform or row filter exists in the DataPolicy
        fun checkAttribute(attribute: DataPolicy.Attribute) {
            if (!validAttributes.contains(attribute.pathString())) {
                throw BadRequestException(
                    BadRequestException.Code.INVALID_ARGUMENT,
                    BadRequest.newBuilder()
                        .addAllFieldViolations(
                            listOf(
                                BadRequest.FieldViolation.newBuilder()
                                    .setField("attribute")
                                    .setDescription("Attribute ${attribute.pathString()} does not exist in source ${dataPolicy.source.ref}")
                                    .build()
                            )
                        )
                        .build()
                )
            }
        }

        dataPolicy.ruleSetsList.forEach { ruleSet ->
            ruleSet.fieldTransformsList.forEach { fieldTransform ->
                checkAttribute(fieldTransform.attribute)
                if (fieldTransform.transformsList.isEmpty()) {
                    throw BadRequestException(
                        BadRequestException.Code.INVALID_ARGUMENT,
                        BadRequest.newBuilder()
                            .addAllFieldViolations(
                                listOf(
                                    BadRequest.FieldViolation.newBuilder()
                                        .setField("fieldTransform")
                                        .setDescription("FieldTransform ${fieldTransform.attribute.pathString()} has no transforms")
                                        .build()
                                )
                            )
                            .build()
                    )
                }
                fieldTransform.transformsList.forEach { transform ->
                    checkPrincipals(transform.principalsList)
                }
                fieldTransform.transformsList.last().let { transform ->
                    if (transform.principalsList.isNotEmpty()) {
                        throw BadRequestException(
                            BadRequestException.Code.INVALID_ARGUMENT,
                            BadRequest.newBuilder()
                                .addAllFieldViolations(
                                    listOf(
                                        BadRequest.FieldViolation.newBuilder()
                                            .setField("fieldTransform")
                                            .setDescription("FieldTransform ${fieldTransform.attribute.pathString()} does not have an empty principals list as last field")
                                            .build()
                                    )
                                )
                                .build()
                        )
                    }
                }
                fieldTransform.transformsList.filter { it.principalsCount == 0 }.let {
                    if (it.size > 1) {
                        throw BadRequestException(
                            BadRequestException.Code.INVALID_ARGUMENT,
                            BadRequest.newBuilder()
                                .addAllFieldViolations(
                                    listOf(
                                        BadRequest.FieldViolation.newBuilder()
                                            .setField("fieldTransform")
                                            .setDescription("FieldTransform ${fieldTransform.attribute.pathString()} has more than one empty principals list")
                                            .build()
                                    )
                                )
                                .build()
                        )
                    }
                }
                // check non-overlapping principals within one fieldTransform
                fieldTransform.transformsList.fold(
                    emptySet<String>(),
                ) { alreadySeen, transform ->
                    transform.principalsList.toSet().let {
                        if (alreadySeen.intersect(it).isNotEmpty()) {
                            throw BadRequestException(
                                BadRequestException.Code.INVALID_ARGUMENT,
                                BadRequest.newBuilder()
                                    .addAllFieldViolations(
                                        listOf(
                                            BadRequest.FieldViolation.newBuilder()
                                                .setField("fieldTransform")
                                                .setDescription("FieldTransform ${fieldTransform.attribute.pathString()} has overlapping principals")
                                                .build()
                                        )
                                    )
                                    .build()
                            )
                        }
                        alreadySeen + it
                    }
                }
            }
            // check for every row filter that the principals overlap with groups in the processing platform
            // and that the attributes exist in the DataPolicy
            ruleSet.filtersList.forEach { filter ->
                filter.conditionsList.forEach { condition ->
                    checkPrincipals(condition.principalsList)
                }
            }
            // check non-overlapping attributes within one ruleset
            ruleSet.fieldTransformsList.map { it.attribute }.fold(
                emptySet<String>(),
            ) { alreadySeen, attribute ->
                attribute.pathString().let {
                    if (alreadySeen.contains(it)) {
                        throw BadRequestException(
                            BadRequestException.Code.INVALID_ARGUMENT,
                            BadRequest.newBuilder()
                                .addAllFieldViolations(
                                    listOf(
                                        BadRequest.FieldViolation.newBuilder()
                                            .setField("ruleSet")
                                            .setDescription("RuleSet has overlapping attributes, ${attribute.pathString()} is already present")
                                            .build()
                                    )
                                )
                                .build()
                        )
                    }
                    alreadySeen + it
                }
            }
        }
    }

    fun getLatestDataPolicy(id: String) = dataPolicyDao.getLatestDataPolicy(id)

    private suspend fun enforceStatement(dataPolicy: DataPolicy) {
        // TODO: replace with switch based on platform identifier instead of type. Possibly multiple platforms of the same type.
        val platform = processingPlatforms.getProcessingPlatform(dataPolicy)
        platform.applyPolicy(dataPolicy)
    }
}
