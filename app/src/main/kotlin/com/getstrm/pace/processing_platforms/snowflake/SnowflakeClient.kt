package com.getstrm.pace.processing_platforms.snowflake

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ProcessingPlatform
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.config.SnowflakeConfig
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.exceptions.throwNotFound
import com.getstrm.pace.processing_platforms.Group
import com.getstrm.pace.processing_platforms.ProcessingPlatformClient
import com.getstrm.pace.util.MILLION_RECORDS
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.THOUSAND_RECORDS
import com.getstrm.pace.util.applyPageParameters
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.withPageInfo
import com.google.rpc.DebugInfo
import com.google.rpc.ResourceInfo
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity

class SnowflakeClient(override val config: SnowflakeConfig) : ProcessingPlatformClient(config) {

    private val snowflakeJwtIssuer =
        SnowflakeJwtIssuer.fromOrganizationAndAccountName(
            privateKey = config.privateKey,
            organizationName = config.organizationName,
            accountName = config.accountName,
            userName = config.userName
        )

    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    private val restTemplate = RestTemplate()

    fun executeRequest(request: SnowflakeRequest): ResponseEntity<SnowflakeResponse> {
        try {
            return restTemplate.postForEntity<SnowflakeResponse>(
                "${config.serverUrl}/api/v2/statements",
                HttpEntity(request, jsonHeaders())
            )
        } catch (e: HttpStatusCodeException) {
            log.warn("Statement: {}", request.statement)
            log.warn("Caused error {} {}", e.message, e.responseBodyAsString)

            throw InternalException(
                InternalException.Code.INTERNAL,
                DebugInfo.newBuilder()
                    .setDetail("Error: ${e.message} - Snowflake error: ${e.responseBodyAsString}")
                    .addAllStackEntries(e.stackTrace.map { it.toString() })
                    .build(),
                e
            )
        }
    }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val statement =
            SnowflakeViewGenerator(dataPolicy)
                .toDynamicViewSQL()
                .sql
                .replace("\\n".toRegex(), "\\\\n")
                .replace("""(\\\d)""".toRegex(), """\\\\""" + "\$1")
        val statementCount =
            statement.mapNotNull { element -> element.takeIf { it == ';' } }.size.toString()
        val request =
            SnowflakeRequest(
                statement = statement,
                database = config.database,
                warehouse = config.warehouse,
                parameters = mapOf("MULTI_STATEMENT_COUNT" to statementCount),
            )
        executeRequest(request)
    }

    val database = SnowflakeDatabase(this, config.database)

    override suspend fun listDatabases(ignored: PageParameters): PagedCollection<Database> {
        return listOf(database).withPageInfo()
    }

    override suspend fun listSchemas(
        databaseId: String,
        pageParameters: PageParameters
    ): PagedCollection<Schema> {
        if (databaseId != database.id) throwNotFound(databaseId, "SnowflakeDatabase")
        return database.listSchemas(pageParameters)
    }

    override suspend fun listTables(
        databaseId: String,
        schemaId: String,
        pageParameters: PageParameters
    ): PagedCollection<Table> {
        if (databaseId != database.id) throwNotFound(databaseId, "SnowflakeDatabase")
        val schema = database.getSchema(schemaId)
        return schema.listTables(pageParameters)
    }

    override suspend fun getTable(databaseId: String, schemaId: String, tableId: String): Table {
        // TODO increase performance
        val table =
            listTables(databaseId, schemaId, THOUSAND_RECORDS).find { it.id == tableId }
                ?: throwNotFound(tableId, "Snowflake table")
        return table
    }

    override suspend fun listGroups(pageParameters: PageParameters): PagedCollection<Group> {
        val request =
            SnowflakeRequest(
                statement = "SHOW ROLES",
            )
        val response = executeRequest(request)

        if (response.body?.message != "Statement executed successfully.") {
            throw RuntimeException(response.body?.message)
        }
        return response.body
            ?.data
            .orEmpty()
            .mapNotNull {
                val owner = it[8]
                if (owner.isEmpty()) {
                    null
                } else {
                    Group(id = it[1], name = it[1])
                }
            }
            .applyPageParameters(pageParameters)
            .withPageInfo()
    }

    private fun jsonHeaders() =
        HttpHeaders().apply {
            setBearerAuth(snowflakeJwtIssuer.issueJwtToken())
            contentType = MediaType.APPLICATION_JSON
            accept = listOf(MediaType.APPLICATION_JSON)
            // Required for Keypair authentication
            set("X-Snowflake-Authorization-Token-Type", "KEYPAIR_JWT")
        }

    fun describeTable(schema: String, table: String): SnowflakeResponse? {
        val request =
            SnowflakeRequest(
                statement = "DESCRIBE TABLE \"$schema\".\"$table\"",
                database = config.database,
                warehouse = config.warehouse,
                schema = schema,
            )
        val snowflakeResponse = executeRequest(request)

        if (snowflakeResponse.body?.message != "Statement executed successfully.") {
            log.error("{}", snowflakeResponse)
            throw RuntimeException(snowflakeResponse.body?.message)
        }
        return snowflakeResponse.body
    }

    inner class SnowflakeDatabase(pp: ProcessingPlatformClient, id: String) :
        Database(
            pp,
            id,
        ) {
        override suspend fun listSchemas(pageParameters: PageParameters): PagedCollection<Schema> {
            val sql =
                """
                SHOW SCHEMAS in DATABASE "$id"
            """
                    .trimIndent()

            val request =
                SnowflakeRequest(
                    statement = sql,
                    database = id,
                    warehouse = config.warehouse,
                )
            return executeRequest(request)
                .body
                ?.data
                .orEmpty()
                .map { (_, name) -> SnowflakeSchema(this, name, name) }
                .withPageInfo()
        }

        override suspend fun getSchema(schemaId: String): Schema {
            return listSchemas(MILLION_RECORDS).find { it.id == schemaId }
                ?: throwNotFound(schemaId, "Snowflake schema")
        }
    }

    inner class SnowflakeSchema(database: Database, id: String, name: String) :
        Schema(
            database,
            id,
            name,
        ) {
        override suspend fun listTables(pageParameters: PageParameters): PagedCollection<Table> {
            val statement =
                """SELECT table_schema, table_name, table_type,
               created as create_date,
               last_altered as modify_date
               FROM information_schema.tables where table_type = 'BASE TABLE'
               ORDER BY table_name
               LIMIT ${pageParameters.pageSize}
               OFFSET ${pageParameters.skip} ;
            """
                    .trimMargin()
            val request =
                SnowflakeRequest(
                    statement = statement,
                    database = config.database,
                    warehouse = config.warehouse,
                )
            return executeRequest(request)
                .body
                ?.data
                .orEmpty()
                .map { (schemaName, tableName) ->
                    val fullName = "$schemaName.$tableName"
                    SnowflakeTable(this, tableName, id)
                }
                .withPageInfo()
        }

        override suspend fun getTable(tableId: String): Table {
            TODO("Not yet implemented")
        }
    }

    data class SnowflakeRequest(
        val statement: String,
        val timeout: Int = 60,
        val resultSetMetaData: Map<String, String> = mapOf("format" to "json"),
        val database: String? = null,
        val schema: String? = null,
        val warehouse: String? = null,
        val parameters: Map<String, String> = emptyMap(),
    )

    inner class SnowflakeTable(
        ppSchema: Schema,
        private val table: String,
        val schema_: String,
    ) : Table(ppSchema, table, table) {
        private val log by lazy { LoggerFactory.getLogger(javaClass) }

        override suspend fun createBlueprint(): DataPolicy {
            return describeTable(schema_, table)
                ?.toDataPolicy(schema.database.platformClient.apiProcessingPlatform, id)
                ?: throw ResourceException(
                    ResourceException.Code.NOT_FOUND,
                    ResourceInfo.newBuilder()
                        .setResourceType("Table")
                        .setResourceName(id)
                        .setDescription(
                            "Table $id not found in Snowflake. Verify that it exists and that the client user has access to it."
                        )
                        .setOwner("Schema: $schema_")
                        .build()
                )
        }

        override val fullName: String
            get() = "${schema.id}.${id}"

        private fun SnowflakeResponse.toDataPolicy(
            platform: ProcessingPlatform,
            fullName: String
        ): DataPolicy {
            return DataPolicy.newBuilder()
                .setMetadata(
                    DataPolicy.Metadata.newBuilder().setTitle(fullName),
                )
                .setPlatform(platform)
                .setSource(
                    DataPolicy.Source.newBuilder()
                        .setRef(fullName)
                        .addAllFields(
                            // Todo: make this more type-safe
                            data.orEmpty().map { (name, type, _, nullable) ->
                                val snowflakeResponse = retrieveColumnTags(name)
                                val tags =
                                    snowflakeResponse.body?.data?.map { it[0] } ?: emptyList()

                                DataPolicy.Field.newBuilder()
                                    .addAllNameParts(listOf(name))
                                    .addAllTags(tags)
                                    .setType(type)
                                    .setRequired(nullable != "y")
                                    .build()
                                    .normalizeType()
                            },
                        )
                        .build(),
                )
                .build()
        }

        private fun retrieveColumnTags(columnName: String): ResponseEntity<SnowflakeResponse> {
            val request =
                SnowflakeRequest(
                    statement =
                        """
                SELECT TAG_NAME, TAG_VALUE FROM TABLE (
                    ${config.database}.INFORMATION_SCHEMA.TAG_REFERENCES(
                        '${schema.id}.$id.$columnName', 'COLUMN'))
                 """
                            .trimIndent(),
                    database = config.database,
                    warehouse = config.warehouse,
                    schema = schema_,
                )

            val snowflakeResponse = executeRequest(request)

            if (snowflakeResponse.body?.message != "Statement executed successfully.") {
                log.error("{}", snowflakeResponse)
                throw RuntimeException(snowflakeResponse.body?.message)
            }
            return snowflakeResponse
        }
    }
}
