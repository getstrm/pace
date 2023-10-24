package com.getstrm.pace.snowflake

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.SNOWFLAKE
import com.getstrm.pace.config.SnowflakeConfig
import com.getstrm.pace.domain.Group
import com.getstrm.pace.exceptions.ProcessingPlatformExecuteException
import com.getstrm.pace.domain.ProcessingPlatformInterface
import com.getstrm.pace.domain.Table
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity

class SnowflakeClient(
    override val id: String,
    private val config: SnowflakeConfig
) : ProcessingPlatformInterface {
    constructor(config: SnowflakeConfig) : this(config.id, config)

    private val snowflakeJwtIssuer = SnowflakeJwtIssuer.fromOrganizationAndAccountName(
        privateKeyResourcePath = config.privateKeyPath,
        organizationName = config.organizationName,
        accountName = config.accountName,
        userName = config.userName
    )

    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    private val restTemplate = RestTemplate()

    override val type
        get() = SNOWFLAKE

    private fun executeRequest(request: SnowflakeRequest): ResponseEntity<SnowflakeResponse> {
        try {
            return restTemplate.postForEntity<SnowflakeResponse>(
                "${config.serverUrl}/api/v2/statements",
                HttpEntity(request, jsonHeaders())
            )
        } catch (e: HttpStatusCodeException) {
            log.warn("Statement: {}", request.statement)
            log.warn("Caused error {} {}", e.message, e.responseBodyAsString)
            throw ProcessingPlatformExecuteException(id, e.responseBodyAsString)
        }
    }

    override suspend fun applyPolicy(dataPolicy: DataPolicy) {
        val statement = SnowflakeDynamicViewGenerator(dataPolicy).toDynamicViewSQL().replace("\\n".toRegex(), "\\\\n")
        val statementCount = statement.mapNotNull { element -> element.takeIf { it == ';' } }.size.toString()
        val request = SnowflakeRequest(
            statement = statement,
            database = config.database,
            warehouse = config.warehouse,
            parameters = mapOf("MULTI_STATEMENT_COUNT" to statementCount),
        )
        executeRequest(request)
    }

    override suspend fun listTables(): List<Table> {
        val statement =
            "select table_schema, table_name, table_type, created as create_date, last_altered as modify_date from information_schema.tables where table_type = 'BASE TABLE';"
        val request = SnowflakeRequest(
            statement = statement,
            database = config.database,
            warehouse = config.warehouse,
        )
        return executeRequest(request).body?.data.orEmpty().map { (schemaName, tableName) ->
            val fullName = "$schemaName.$tableName"
            SnowflakeTable(fullName, tableName, schemaName, this)
        }
    }

    fun describeTable(schema: String, table: String): SnowflakeResponse? {
        val request = SnowflakeRequest(
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

    override suspend fun listGroups(): List<Group> {
        val request = SnowflakeRequest(
            statement = "SHOW ROLES",
        )
        val response = executeRequest(request)

        if (response.body?.message != "Statement executed successfully.") {
            throw RuntimeException(response.body?.message)
        }
        return response.body?.data.orEmpty().mapNotNull {
            val owner = it[8]
            if (owner.isEmpty()) {
                null
            } else {
                Group(id = it[1], name = it[1])
            }
        }
    }

    private fun jsonHeaders() = HttpHeaders().apply {
        setBearerAuth(snowflakeJwtIssuer.issueJwtToken())
        contentType = MediaType.APPLICATION_JSON
        accept = listOf(MediaType.APPLICATION_JSON)
        // Required for Keypair authentication
        set("X-Snowflake-Authorization-Token-Type", "KEYPAIR_JWT")
    }
}

private data class SnowflakeRequest(
    val statement: String,
    val timeout: Int = 60,
    val resultSetMetaData: Map<String, String> = mapOf("format" to "json"),
    val database: String? = null,
    val schema: String? = null,
    val warehouse: String? = null,
    val parameters: Map<String, String> = emptyMap(),
)

class SnowflakeTable(
    override val fullName: String,
    private val table: String,
    private val schema: String,
    private val client: SnowflakeClient,
) : Table() {
    override suspend fun toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy {
        TODO()
        // return client.describeTable(schema, table)?.toDataPolicy(platform, this) ?:  throw NotFoundException(table)
    }
}
