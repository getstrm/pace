package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.data_policies.v1alpha.EvaluateDataPolicyResponse.PrincipalEvaluationResult
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.processing_platforms.h2.H2Client
import com.getstrm.pace.processing_platforms.h2.H2ViewGenerator
import com.getstrm.pace.util.uniquePrincipals
import org.springframework.stereotype.Component

@Component
class DataPolicyEvaluationService {

    /**
     * Evaluates the first ruleset in the provided data policy on the provided input CSV.
     */
    fun evaluatePolicy(dataPolicy: DataPolicy, inputCsv: String): List<PrincipalEvaluationResult> {
        val h2Client = H2Client()
        h2Client.insertCSV(dataPolicy, inputCsv, "input")
        val principalsToEvaluate = dataPolicy.ruleSetsList.first().uniquePrincipals() + null

        val results = principalsToEvaluate.map { principal ->
            val viewGenerator = H2ViewGenerator(
                dataPolicy,
                principal,
                "input",
            )
            val select = viewGenerator.toSelectStatement(dataPolicy.ruleSetsList.first())
            val resultCsv = h2Client.jooq.fetch(select).formatCSV()

            PrincipalEvaluationResult.newBuilder().apply {
                if (principal != null) setPrincipal(principal)
            }
                .setCsv(resultCsv)
                .build()
        }
        h2Client.close()
        return results
    }
}
