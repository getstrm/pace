package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.util.pathString
import com.google.rpc.BadRequest
import com.google.rpc.BadRequest.FieldViolation
import org.springframework.stereotype.Component

// TODO improve readability
//      the exceptions and nested functions make it hard to follow
@Component
class DataPolicyValidatorService {

    fun validate(dataPolicy: DataPolicy, validGroups: Set<String>) {
        if (dataPolicy.source.ref.isNullOrEmpty()) {
            throw invalidArgumentException("dataPolicy.source.ref", "DataPolicy source ref is empty")
        }
        if (dataPolicy.platform.id.isNullOrEmpty()) {
            throw invalidArgumentException("dataPolicy.platform.id", "DataPolicy platform id is empty")
        }
        val validFields = dataPolicy.source.fieldsList.map(DataPolicy.Field::pathString).toSet()

        // check that every principal exists in validGroups
        fun checkPrincipals(principals: List<DataPolicy.Principal>) {
            (principals.map { it.group }.toSet() - validGroups).let {
                if (it.isNotEmpty()) {
                    throw invalidArgumentException(
                        it.map { principal ->
                            FieldViolation.newBuilder()
                                .setField("principal")
                                .setDescription("Principal $principal does not exist in platform ${dataPolicy.platform.id}")
                                .build()
                        }
                    )
                }
            }
        }

        // check that every field in a field transform or row filter exists in the DataPolicy
        fun checkField(field: DataPolicy.Field) {
            if (!validFields.contains(field.pathString())) {
                throw invalidArgumentException(
                    field.pathString(),
                    "Field does not exist in source ${dataPolicy.source.ref}"
                )
            }
        }

        dataPolicy.ruleSetsList.forEach { ruleSet ->
            ruleSet.fieldTransformsList.forEach { fieldTransform ->
                checkField(fieldTransform.field)
                if (fieldTransform.transformsList.isEmpty()) {
                    throw invalidArgumentException(
                        "fieldTransform",
                        "FieldTransform ${fieldTransform.field.pathString()} has no transforms"
                    )
                }

                fieldTransform.transformsList.forEach { transform ->
                    checkPrincipals(transform.principalsList)
                }

                fieldTransform.transformsList.last().let { transform ->
                    if (transform.principalsList.isNotEmpty()) {
                        throw invalidArgumentException(
                            "fieldTransform",
                            "FieldTransform ${fieldTransform.field.pathString()} does not have an empty principals list as last field"
                        )
                    }
                }

                fieldTransform.transformsList.filter { it.principalsCount == 0 }.let {
                    if (it.size > 1) {
                        throw invalidArgumentException(
                            "fieldTransform",
                            "FieldTransform ${fieldTransform.field.pathString()} has more than one empty principals list"
                        )
                    }
                }

                // check non-overlapping principals within one fieldTransform
                fieldTransform.transformsList.fold(
                    emptySet<String>(),
                ) { alreadySeen, transform ->
                    transform.principalsList.map { it.group }.toSet().let {
                        if (alreadySeen.intersect(it).isNotEmpty()) {
                            throw invalidArgumentException(
                                "fieldTransform",
                                "FieldTransform ${fieldTransform.field.pathString()} has overlapping principals"
                            )
                        }

                        alreadySeen + it
                    }
                }
            }
            // check for every row filter that the principals overlap with groups in the processing platform
            // and that the fields exist in the DataPolicy
            ruleSet.filtersList.forEach { filter ->
                filter.conditionsList.forEach { condition ->
                    checkPrincipals(condition.principalsList)
                }
            }
            // check non-overlapping fields within one ruleset
            ruleSet.fieldTransformsList.map { it.field }.fold(
                emptySet<String>(),
            ) { alreadySeen, field ->
                field.pathString().let {
                    if (alreadySeen.contains(it)) {
                        throw invalidArgumentException(
                            listOf(
                                FieldViolation.newBuilder()
                                    .setField("ruleSet")
                                    .setDescription("RuleSet has overlapping fields, ${field.pathString()} is already present")
                                    .build()
                            )
                        )
                    }

                    alreadySeen + it
                }
            }
        }
    }

    private fun invalidArgumentException(fieldName: String, description: String) =
        invalidArgumentException(
            listOf(
                FieldViolation.newBuilder()
                    .setField(fieldName)
                    .setDescription(description)
                    .build()
            )
        )

    private fun invalidArgumentException(fieldViolations: List<FieldViolation>) =
        BadRequestException(
            BadRequestException.Code.INVALID_ARGUMENT,
            BadRequest.newBuilder()
                .addAllFieldViolations(fieldViolations)
                .build()
        )
}
