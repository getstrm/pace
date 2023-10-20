package com.getstrm.daps.snowflake

import com.getstrm.daps.domain.ProcessingPlatformExecuteException
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy.ProcessingPlatform.PlatformType.SNOWFLAKE
import com.getstrm.daps.config.SnowflakeConfig
import com.getstrm.daps.dao.Tokens
import com.getstrm.daps.dao.TokensDao
import com.getstrm.daps.domain.Group
import com.getstrm.daps.domain.ProcessingPlatformInterface
import com.getstrm.daps.domain.Table
import com.getstrm.jooq.generated.tables.records.ProcessingPlatformTokensRecord
import io.github.bonigarcia.wdm.WebDriverManager
import kotlinx.coroutines.runBlocking
import org.apache.commons.io.output.NullOutputStream
import org.openqa.selenium.By
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity
import toOffsetDateTime
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class SnowflakeClient(
    override val id: String,
    private val url: String,
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String,
    private val database: String,
    private val warehouse: String,
    private val tokensDao: TokensDao,
) : ProcessingPlatformInterface {

    constructor(config: SnowflakeConfig, tokensDao: TokensDao) : this(
        id = config.id,
        url = config.serverUrl,
        clientId = config.clientId,
        clientSecret = config.clientSecret,
        redirectUri = config.redirectUri,
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
        if (tokens == null) {
            runBlocking { getAccessToken() }
        }
    }

    override val type
        get() = SNOWFLAKE


    private fun executeRequest(request: SnowflakeRequest): ResponseEntity<SnowflakeResponse> {
        handleTokenExpiry()
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
        setBearerAuth(tokens?.accessToken.orEmpty())
        contentType = MediaType.APPLICATION_JSON
        accept = listOf(MediaType.APPLICATION_JSON)
        }

    private fun getAccessToken(refresh: Boolean = false) {
        val requestBody = if (refresh) {
            LinkedMultiValueMap(
                mapOf(
                    "grant_type" to listOf("refresh_token"),
                    "refresh_token" to listOf(tokens?.refreshToken.orEmpty()),
                )
            )
        } else {
            val tokenCode = getAuthenticationCode()
            LinkedMultiValueMap(
                mapOf(
                    "grant_type" to listOf("authorization_code"),
                    "code" to listOf(tokenCode),
                    "redirect_uri" to listOf(redirectUri),
                )
            )
        }

        val request = HttpEntity(
            requestBody,
            HttpHeaders().apply {
                accept = listOf(MediaType.ALL)
                contentType = MediaType.APPLICATION_FORM_URLENCODED
                setBasicAuth(clientId, clientSecret)
            },
        )

        restTemplate.postForEntity<Tokens>("$url/oauth/token-request", request).also {
            tokensDao.upsertToken(id, it.body!!)
        }
    }

    private fun getAuthenticationCode(): String {
        log.debug("Starting chromedriver")
        WebDriverManager.chromedriver().setup()
        java.util.logging.Logger.getLogger("org.openqa.selenium").level = java.util.logging.Level.OFF

        val options = ChromeOptions().addArguments("--headless=new").addArguments("--no-sandbox")
            .addArguments("--remote-allow-origins=*") // fix connection failed issue
        val service = ChromeDriverService.Builder().build()
        service.sendOutputTo(NullOutputStream.INSTANCE)
        val driver = ChromeDriver(service, options)

        log.debug("Authorizing via chromedriver")
        driver.get(
            "$url/oauth/authorize?response_type=code&client_id=${URLEncoder.encode(clientId, "utf-8")}&redirect_uri=${
                URLEncoder.encode(
                    redirectUri,
                    "utf-8",
                )
            }",
        )
        WebDriverWait(
            driver,
            Duration.ofSeconds(5)
        ).until(ExpectedConditions.presenceOfElementLocated(By.name("username")))

        log.debug("Received web page via chromedriver")
        // TODO!
        driver.findElement(By.name("username")).sendKeys("ivangetstrm")
        driver.findElement(By.name("password")).sendKeys("TPG7wux9gmb3uza!cxq")
        driver.findElement(By.xpath("//*[text() = 'Sign in']")).click()
        log.debug("Typed in credentials via chromedriver")
        runCatching {
            WebDriverWait(
                driver,
                Duration.ofSeconds(5)
            ).until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[text() = 'Allow']")))
            driver.findElement(By.xpath("//*[text() = 'Allow']")).click()
        }
        WebDriverWait(driver, Duration.ofSeconds(5)).until {
            it.currentUrl.contains(redirectUri)
        }
        val authenticationCode = driver.currentUrl.split("code=")[1]
        driver.quit()
        log.debug("Done with chromedriver")
        return authenticationCode
    }

    private fun handleTokenExpiry() {
        val now = Instant.now().toOffsetDateTime()
        val deltaRefresh = tokens!!.refreshTokenExpiresAt!!.until(now, ChronoUnit.SECONDS)
        val deltaAccess = tokens!!.expiresAt!!.until(now, ChronoUnit.SECONDS)
        if (tokens == null || deltaRefresh > -60) {
            getAccessToken()
        } else {
            // refresh if either are within 60 seconds of expiry
            if (deltaAccess <= -60) {
                getAccessToken(refresh = true)
            }
        }
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
