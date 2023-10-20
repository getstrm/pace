package com.getstrm.daps.snowflake

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.SNOWFLAKE
import com.getstrm.daps.config.SnowflakeConfig
import com.getstrm.daps.dao.TokensDao
import com.getstrm.daps.domain.Group
import com.getstrm.daps.domain.ProcessingPlatformExecuteException
import com.getstrm.daps.domain.ProcessingPlatformInterface
import com.getstrm.daps.domain.Table
import com.getstrm.jooq.generated.tables.records.ProcessingPlatformTokensRecord
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
    private val url: String,
    private val database: String,
    private val warehouse: String,
    private val tokensDao: TokensDao,
) : ProcessingPlatformInterface {

    private val snowflakeJwtIssuer = SnowflakeJwtIssuer(
        privateKeyResourcePath = "daps-snowflake-private-key.p8",
        accountName = "RQ45750",
        userName = "ivangetstrm"
    )

    constructor(config: SnowflakeConfig, tokensDao: TokensDao) : this(
        id = config.id,
        url = config.serverUrl,
        database = config.database,
        warehouse = config.warehouse,
        tokensDao = tokensDao,
    )

    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    private val restTemplate = RestTemplate()
    private var tokens: ProcessingPlatformTokensRecord? = null

    init {
        tokensDao.getRecord(id)?.let {
            log.debug("Retrieved access token for $id from database")
            tokens = it
        }
    }

    override val type
        get() = SNOWFLAKE


    private fun executeRequest(request: SnowflakeRequest): ResponseEntity<SnowflakeResponse> {
        try {
            return restTemplate.postForEntity<SnowflakeResponse>(
                "$url/api/v2/statements",
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
            database = database,
            warehouse = warehouse,
            parameters = mapOf("MULTI_STATEMENT_COUNT" to statementCount),
        )
        executeRequest(request)
    }

    override suspend fun listTables(): List<Table> {
        val statement =
            "select table_schema, table_name, table_type, created as create_date, last_altered as modify_date from information_schema.tables where table_type = 'BASE TABLE';"
        val request = SnowflakeRequest(
            statement = statement,
            database = database,
            warehouse = warehouse,
        )
        return executeRequest(request).body?.data.orEmpty().map { (schemaName, tableName) ->
            val fullName = "$schemaName.$tableName"
            SnowflakeTable(fullName, tableName, schemaName, this)
        }
    }

    fun describeTable(schema: String, table: String): SnowflakeResponse? {
        val request = SnowflakeRequest(
            statement = "DESCRIBE TABLE \"$schema\".\"$table\"",
            database = database,
            warehouse = warehouse,
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
