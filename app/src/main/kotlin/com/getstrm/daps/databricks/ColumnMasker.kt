package com.getstrm.daps.databricks

import com.databricks.sdk.service.sql.StatementState
import io.strmprivacy.api.entities.v1.DataContract
import io.strmprivacy.api.entities.v1.PurposeMapping
import io.strmprivacy.api.entities.v1.SimpleSchemaNodeType

// TODO can this be discarded?
class ColumnMasker(
    private val databricksClient: DatabricksClient,
) {

    fun maskPiiColumns(dataContract: DataContract, purposeMappings: List<PurposeMapping>) {
        val (catalog, schema, table) = dataContract.ref.name.split(".")

        // This creates a map from purpose level to group names used in databricks.
        // E.g. level 1 -> purpose:marketing.
        val purposeGroupsByLevel = purposeMappings.associate {
            it.level to "purpose:${it.purpose.lowercase().trim()}"
        }

        val piiFieldsWithLevel = dataContract.fieldMetadataList.filter {
            it.hasPersonalDataConfig() && it.personalDataConfig.isPii && it.personalDataConfig.purposeLevel > 0
        }.associate {
            it.fieldName to it.personalDataConfig.purposeLevel
        }

        // Create functions for the masking, two per purpose level, one for string and one for long
        purposeGroupsByLevel.forEach { (level, group) ->
            val stringMasker = """
                CREATE OR REPLACE FUNCTION mask_pii_string_$level(value STRING)
                RETURN IF(is_account_group_member('$group'), value, '****');
            """.trimIndent()

            val bigintMasker = """
                CREATE OR REPLACE FUNCTION mask_pii_bigint_$level(value BIGINT)
                RETURN IF(is_account_group_member('$group'), value, NULL);
            """.trimIndent()

            listOf(stringMasker, bigintMasker).forEach { masker ->
                val resp = databricksClient.executeStatement(masker, catalog, schema)
                if (resp.status.state == StatementState.SUCCEEDED) {
                    println("Masker function created successfully for level $level")
                } else {
                    println("Masker function creation status: ${resp.status.state} (error: ${resp.status.error})")
                }
            }
        }

        // Alter the table to use the masking functions
        for (node in dataContract.schema.simpleSchema.nodesList) {
            if (piiFieldsWithLevel.containsKey(node.name)) {
                val level = piiFieldsWithLevel[node.name]
                if (node.type !== SimpleSchemaNodeType.STRING && node.type !== SimpleSchemaNodeType.LONG) {
                    println("Column ${node.name} is not a string or long, skipping")
                    continue
                }
                val maskFunction = if (node.type == SimpleSchemaNodeType.STRING) {
                    "mask_pii_string_$level"
                } else {
                    "mask_pii_bigint_$level"
                }
                val query = """
                    ALTER TABLE ${dataContract.ref.name} 
                    ALTER COLUMN ${node.name} 
                    SET MASK $maskFunction;
                """.trimIndent()
                val resp = databricksClient.executeStatement(query, catalog, schema)
                if (resp.status.state == StatementState.SUCCEEDED) {
                    println("Column ${node.name} altered successfully")
                } else {
                    println("Column ${node.name} alteration status: ${resp.status.state} (error: ${resp.status.error})")
                }
            }
        }
    }

    fun dropMasks(dataContract: DataContract) {
        val (catalog, schema, table) = dataContract.ref.name.split(".")
        for (metadata in dataContract.fieldMetadataList) {
            val query = """
                ALTER TABLE ${dataContract.ref.name} 
                ALTER COLUMN ${metadata.fieldName} 
                DROP MASK;
            """.trimIndent()
            val resp = databricksClient.executeStatement(query, catalog, schema)
            if (resp.status.state == StatementState.SUCCEEDED) {
                println("Column ${metadata.fieldName} altered successfully")
            } else {
                println("Column ${metadata.fieldName} alteration status: ${resp.status.state} (error: ${resp.status.error})")
            }
        }
    }
}
