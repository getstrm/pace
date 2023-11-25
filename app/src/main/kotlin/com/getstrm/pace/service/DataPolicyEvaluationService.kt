package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.EvaluateDataPolicyResponse.FullEvaluationResult
import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.EvaluateDataPolicyResponse.FullEvaluationResult.RuleSetResult.PrincipalEvaluationResult
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.processing_platforms.h2.H2Client
import com.getstrm.pace.processing_platforms.h2.H2ViewGenerator
import com.getstrm.pace.util.uniquePrincipals
import com.google.rpc.DebugInfo
import org.h2.jdbc.JdbcSQLSyntaxErrorException
import org.jooq.CSVFormat
import org.jooq.exception.DataAccessException
import org.springframework.stereotype.Component

@Component
class DataPolicyEvaluationService {

    /**
     * Evaluates the first ruleset in the provided data policy on the provided input CSV.
     */
    fun evaluatePolicy(
        dataPolicy: DataPolicy,
        sampleCsv: String,
    ): FullEvaluationResult {
        val h2Client = H2Client()
        try {
            h2Client.insertCSV(dataPolicy, sampleCsv, "input")
            return FullEvaluationResult.newBuilder()
                .addAllRuleSetResults(
                    dataPolicy.ruleSetsList.map {
                        FullEvaluationResult.RuleSetResult.newBuilder()
                            .setTarget(it.target)
                            .addAllPrincipalEvaluationResults(evaluateRuleSet(dataPolicy, it, h2Client))
                            .build()
                    }
                ).build()
        } catch (e: DataAccessException) {
            val (detail, cause) = when (val cause = e.cause) {
                is JdbcSQLSyntaxErrorException -> cause.originalMessage to cause
                else -> (cause?.message ?: e.message) to e
            }
            val debugInfo = DebugInfo.newBuilder()
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
        h2Client: H2Client
    ): List<PrincipalEvaluationResult> {
        val principalsToEvaluate = ruleSet.uniquePrincipals() + null

        val results = principalsToEvaluate.map { principal ->
            val viewGenerator = H2ViewGenerator(
                dataPolicy,
                principal,
                "input",
            )
            val select = viewGenerator.toSelectStatement(ruleSet)
            val resultCsv = h2Client.jooq.fetch(select).formatCSV(
                CSVFormat().nullString("").emptyString("")
            )

            PrincipalEvaluationResult.newBuilder().apply {
                if (principal != null) setPrincipal(principal)
            }
                .setCsv(resultCsv)
                .build()
        }
        return results
    }
}
