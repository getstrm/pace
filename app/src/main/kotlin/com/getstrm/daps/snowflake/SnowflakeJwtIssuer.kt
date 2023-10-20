package com.getstrm.daps.snowflake

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.*


/**
 * Based on https://github.com/snowflakedb/snowflake-jdbc/blob/b0841291cdc6974b42b47617924dd84f3e0b9a45/src/main/java/net/snowflake/client/core/SessionUtilKeyPair.java
 */
class SnowflakeJwtIssuer(
    privateKeyResourcePath: String,
    accountName: String,
    userName: String
) {
    private val userName: String
    private val accountName: String
    private val privateKey: RSAPrivateCrtKey
    private var publicKey: PublicKey? = null

    private val keyFactoryInstance = KeyFactory.getInstance("RSA")

    init {
        this.userName = userName.uppercase(Locale.getDefault())
        this.accountName = accountName.uppercase(Locale.getDefault())

        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKeyBytes = File(
            this::class.java.classLoader.getResource(privateKeyResourcePath)?.toURI() ?: throw IllegalArgumentException(
                "Private key not found"
            )
        ).readBytes()
        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val privateKey = keyFactory.generatePrivate(keySpec)

        this.privateKey = privateKey as RSAPrivateCrtKey
        val publicKeySpec = RSAPublicKeySpec(this.privateKey.modulus, this.privateKey.publicExponent)

        try {
            this.publicKey = keyFactoryInstance.generatePublic(publicKeySpec)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalArgumentException("Error retrieving public key", e)
        } catch (e: InvalidKeySpecException) {
            throw IllegalArgumentException("Error retrieving public key", e)
        }
    }

    fun issueJwtToken(): String {
        val builder = JWTClaimsSet.Builder()
        val sub = String.format(SUBJECT_FMT, accountName, userName)
        val iss = String.format(
            ISSUER_FMT,
            accountName,
            userName,
            calculatePublicKeyFingerprint(publicKey)
        )

        // iat is now
        val iat = Date(System.currentTimeMillis())

        // expiration is 60 seconds later
        val exp = Date(iat.time + 60L * 1000)
        val claimsSet: JWTClaimsSet = builder.issuer(iss).subject(sub).issueTime(iat).expirationTime(exp).build()
        val signedJWT = SignedJWT(JWSHeader(JWSAlgorithm.RS256), claimsSet)
        val signer: JWSSigner = RSASSASigner(privateKey)
        try {
            signedJWT.sign(signer)
        } catch (e: JOSEException) {
            throw IllegalStateException("Error signing JWT", e)
        }
        // Log the contents of the token, displaying expiration and issue time in epoch time
        log.debug(
            "JWT:\n'{'\niss: {}\nsub: {}\niat: {}\nexp: {}\n'}'",
            iss,
            sub, (iat.time / 1000).toString(), (exp.time / 1000).toString()
        )
        return signedJWT.serialize()
    }

    private fun calculatePublicKeyFingerprint(publicKey: PublicKey?): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val sha256Hash = md.digest(publicKey!!.encoded)
            "SHA256:" + Base64.encodeBase64String(sha256Hash)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Error when calculating fingerprint", e)
        }
    }

    companion object {
        private val log by lazy { LoggerFactory.getLogger(SnowflakeJwtIssuer::class.java) }
        private const val ISSUER_FMT = "%s.%s.%s"
        private const val SUBJECT_FMT = "%s.%s"
    }
}
