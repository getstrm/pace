package io.strmprivacy.management.data_policy_service.databricks

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.daps.config.ProcessingPlatformConfig
import com.getstrm.daps.databricks.DatabricksClient
import com.getstrm.daps.databricks.DatabricksDynamicViewGenerator
import com.getstrm.daps.databricks.toDataPolicy
import kotlinx.coroutines.runBlocking

fun main() {
    val databricksClient = DatabricksClient(
        id = "hoi",
        ProcessingPlatformConfig.DatabricksConfig(
            workspaceHost = "https://dbc-f822d343-8733.cloud.databricks.com",
            accountHost = "https://accounts.cloud.databricks.com",
            accountId = "b327b34e-82c3-4efc-ae24-a29c3e917651",
            clientId = "78c80306-37a5-49b9-ae75-dd9e15a93727",
            clientSecret = "dosea3be942f4f457e32b3ae614d63addfbc",
            warehouseId = "3346a0ea3ab1c8db",
            catalog = "strm",
        )
    )

    runBlocking { println(databricksClient.listGroups()) }

    val gddemoTable = databricksClient.listSchemaTables("strm", "poc").first { it.name == "gddemo" }
    // Example of how to convert a table to a DataPolicy and then to a Databricks Dynamic View
    val dataPolicy = gddemoTable.toDataPolicy(DataPolicy.ProcessingPlatform.getDefaultInstance())
    // Note: this is where the policy should be enriched with field transforms and/or row filters
    val dynamicViewSql = DatabricksDynamicViewGenerator(dataPolicy).toDynamicViewSQL()
    databricksClient.executeStatement(dynamicViewSql, "strm", "poc")
}
