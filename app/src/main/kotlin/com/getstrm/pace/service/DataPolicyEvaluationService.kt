package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.EvaluateDataPolicyRequest
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.EvaluateDataPolicyResponse.RuleSetResult
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.EvaluateDataPolicyResponse.RuleSetResult.EvaluationResult
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.EvaluateDataPolicyResponse.RuleSetResult.EvaluationResult.CsvEvaluation
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.Principal
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.internalExceptionOneOfNotProvided
import com.getstrm.pace.processing_platforms.h2.H2Client
import com.getstrm.pace.processing_platforms.h2.H2ViewGenerator
import com.getstrm.pace.util.uniquePrincipals
import com.google.rpc.DebugInfo
import org.h2.jdbc.JdbcSQLSyntaxErrorException
import org.jooq.CSVFormat
import org.jooq.exception.DataAccessException
import org.springframework.stereotype.Component

@Component
class DataPolicyEvaluationService(
    private val dataPolicyService: DataPolicyService,
) {
    fun evaluate(request: EvaluateDataPolicyRequest): List<RuleSetResult> {
        val policy =
            when (request.dataPolicyCase) {
                EvaluateDataPolicyRequest.DataPolicyCase.DATA_POLICY_REF ->
                    with(request.dataPolicyRef) {
                        dataPolicyService.getLatestDataPolicy(dataPolicyId, platformId)
                    }
                EvaluateDataPolicyRequest.DataPolicyCase.INLINE_DATA_POLICY ->
                    request.inlineDataPolicy
                EvaluateDataPolicyRequest.DataPolicyCase.DATAPOLICY_NOT_SET,
                null -> throw internalExceptionOneOfNotProvided()
            }

        return when (request.sampleDataCase) {
            EvaluateDataPolicyRequest.SampleDataCase.CSV_SAMPLE ->
                evaluateCsvPolicy(policy, request.principalsList, request.csvSample.csv)
            EvaluateDataPolicyRequest.SampleDataCase.SAMPLEDATA_NOT_SET,
            null -> throw internalExceptionOneOfNotProvided()
        }
    }

    /** Evaluates the first ruleset in the provided data policy on the provided input CSV. */
    private fun evaluateCsvPolicy(
        dataPolicy: DataPolicy,
        principals: List<Principal>,
        sampleCsv: String,
    ): List<RuleSetResult> {
        val h2Client = H2Client()
        try {
            h2Client.insertCSV(dataPolicy, sampleCsv, "input")
            return dataPolicy.ruleSetsList.map {
                RuleSetResult.newBuilder()
                    .setTarget(it.target)
                    .addAllEvaluationResults(evaluateRuleSet(dataPolicy, it, h2Client, principals))
                    .build()
            }
        } catch (e: DataAccessException) {
            val (detail, cause) =
                when (val cause = e.cause) {
                    is JdbcSQLSyntaxErrorException -> cause.originalMessage to cause
                    else -> (cause?.message ?: e.message) to e
                }
            val debugInfo =
                DebugInfo.newBuilder()
                    .setDetail(
                        "Error while evaluating data policy. If caused by platform-specific statements, " +
                            "please test the data policy on the platform itself. Details: $detail"
                    )
                    .build()
            throw InternalException(InternalException.Code.UNKNOWN, debugInfo, cause)
        } finally {
            h2Client.close()
        }
    }

    private fun evaluateRuleSet(
        dataPolicy: DataPolicy,
        ruleSet: DataPolicy.RuleSet,
        h2Client: H2Client,
        principals: List<Principal>
    ): List<EvaluationResult> {
        val principalsToEvaluate =
            if (principals.isEmpty()) {
                ruleSet.uniquePrincipals() + null
            } else {
                principals.mapTo(mutableSetOf()) {
                    when (it.principalCase) {
                        Principal.PrincipalCase.GROUP -> it
                        Principal.PrincipalCase.PRINCIPAL_NOT_SET,
                        null -> null
                    }
                }
            }

        val results =
            principalsToEvaluate.map { principal ->
                val viewGenerator =
                    H2ViewGenerator(
                        dataPolicy,
                        principal,
                        "input",
                    )
                val select = viewGenerator.toSelectStatement(ruleSet)
                val resultCsv =
                    h2Client.jooq
                        .fetch(select)
                        .formatCSV(CSVFormat().nullString("").emptyString(""))

                EvaluationResult.newBuilder()
                    .apply { if (principal != null) setPrincipal(principal) }
                    .setCsvEvaluation(CsvEvaluation.newBuilder().setCsv(resultCsv).build())
                    .build()
            }
        return results
    }
}
