package com.getstrm.daps.dao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.getstrm.jooq.generated.tables.ProcessingPlatformTokens.Companion.PROCESSING_PLATFORM_TOKENS
import com.getstrm.jooq.generated.tables.records.ProcessingPlatformTokensRecord
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.threeten.extra.Seconds
import toOffsetDateTime
import java.time.Instant
import java.time.OffsetDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class Tokens(
    @JsonProperty("access_token") val accessToken: String?,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("refresh_token_expires_in") val refreshTokenExpiresIn: Int?,
    @JsonProperty("expires_in") val expiresIn: Int?,
)

@Component
class TokensDao(
    val jooq: DSLContext,
) {
    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    /**
     * called with a fresh token (from the server)
     */
    fun upsertToken(platformId: String, tokens: Tokens, context: DSLContext = jooq): ProcessingPlatformTokensRecord? {
        val record =
            getRecord(platformId) ?: jooq.newRecord(PROCESSING_PLATFORM_TOKENS)

        // possibly partially update the record, if `tokens` has been partially filled in.
        record.apply {
            this.platformId = platformId
            this.accessToken = tokens.accessToken ?: this.accessToken
            this.refreshToken = tokens.refreshToken ?: this.refreshToken
            this.expiresAt = tokens.expiresIn?.toOffsetDateTime() ?: this.expiresAt
            this.refreshTokenExpiresAt = tokens.refreshTokenExpiresIn?.toOffsetDateTime() ?: this.refreshTokenExpiresAt
        }

        if (record.accessToken == null || record.refreshToken == null || record.expiresAt == null || record.refreshTokenExpiresAt == null) {
            log.warn("Not storing (partially) empty tokens for platform {} {}", platformId, record.formatJSON())
            return null
        }
        return record.also { it.store() }
    }

    fun getRecord(id: String): ProcessingPlatformTokensRecord? =
        jooq.selectFrom(PROCESSING_PLATFORM_TOKENS)
            .where(PROCESSING_PLATFORM_TOKENS.PLATFORM_ID.eq(id))
            .fetchOne()
}

/**
 * map a relative number of seconds to an OffsetDateTime from the current moment.
 */
fun Int?.toOffsetDateTime(): OffsetDateTime? =
    this?.let { ((Instant.now() + Seconds.of(this))).toOffsetDateTime() }
