// PRODUCTION WARNING: This is an in-memory implementation for development.
// Replace with a persistent database (PostgreSQL, DynamoDB, etc.) before production deployment.
// Required: encrypted token storage, TTL for OAuth states, audit logging.

package com.enterprise.pos.backend.storage

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * An OAuth token bundle for a third-party provider.
 *
 * In production this structure must be encrypted at rest (AES-256-GCM) and
 * the encryption key must be stored in a hardware security module (HSM) or
 * cloud KMS. The in-memory implementation below stores tokens in plaintext
 * which is acceptable only for development and integration testing.
 */
@Serializable
data class ProviderToken(
    val provider: String,
    val merchantId: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val scope: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * In-memory token vault for OAuth access tokens received from Shopify, Square,
 * and other external providers.
 *
 * **Security warning:**
 * This implementation stores tokens in plaintext in memory. In production:
 *   1. Replace with a database (PostgreSQL, DynamoDB, etc.)
 *   2. Encrypt accessToken and refreshToken before storage (AES-256-GCM)
 *   3. Use envelope encryption: encrypt token fields with a data key, encrypt
 *      the data key with a master key in AWS KMS / Azure Key Vault / GCP Cloud KMS
 *   4. Rotate secrets regularly; never log tokens
 *   5. Implement automatic refresh using refreshToken before expiresAt
 */
class TokenVault {

    private val tokens = ConcurrentHashMap<String, ProviderToken>() // "provider:merchantId" -> ProviderToken
    private val oauthStates = ConcurrentHashMap<String, String>() // state -> provider (shopify, square)
    private val logger = org.slf4j.LoggerFactory.getLogger(TokenVault::class.java)

    // SECURITY WARNING: Never log accessToken or refreshToken values.
    private fun redacted(token: String?): String = token?.take(4)?.plus("…REDACTED") ?: "null"

    private fun key(provider: String, merchantId: String): String = "$provider:$merchantId"

    /** Store a token in the vault. Overwrites any existing token for the same provider+merchant. */
    fun storeToken(token: ProviderToken) {
        tokens[key(token.provider, token.merchantId)] = token
        logger.info(
            "Stored token for provider {} merchant {} (expiresAt: {}, accessToken: {})",
            token.provider, token.merchantId, token.expiresAt ?: "never", redacted(token.accessToken)
        )
    }

    /** Retrieve a token by provider and merchant ID. */
    fun getToken(provider: String, merchantId: String): ProviderToken? {
        return tokens[key(provider, merchantId)]
    }

    /** Remove a token from the vault (e.g., on disconnect or expiry). */
    fun removeToken(provider: String, merchantId: String) {
        tokens.remove(key(provider, merchantId))
        logger.info("Removed token for provider {} merchant {}", provider, merchantId)
    }

    /** List all stored tokens (without sensitive values) for debugging. */
    fun listTokenMetadata(): List<ProviderTokenMetadata> {
        return tokens.values.map { token ->
            ProviderTokenMetadata(
                provider = token.provider,
                merchantId = token.merchantId,
                scope = token.scope,
                createdAt = token.createdAt,
                expiresAt = token.expiresAt,
                hasRefreshToken = token.refreshToken != null
            )
        }
    }

    // OAuth state management for CSRF protection

    /** Store a valid OAuth state nonce and its associated provider. */
    fun storeOAuthState(state: String, provider: String) {
        oauthStates[state] = provider
    }

    /** Consume (remove) a state nonce and return the associated provider if valid. */
    fun consumeOAuthState(state: String): String? {
        return oauthStates.remove(state)
    }

    /** Purge expired states. In this in-memory implementation states never expire,
     * but in production they should have a TTL of 10 minutes. */
    @Suppress("EmptyFunctionBlock") // In-memory OAuth state has no TTL
    fun purgeExpiredStates() {
        // No-op for in-memory; production should use Redis with TTL
    }
}

/**
 * Non-sensitive metadata about a stored token, safe to log or return in admin APIs.
 */
@Serializable
data class ProviderTokenMetadata(
    val provider: String,
    val merchantId: String,
    val scope: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val hasRefreshToken: Boolean
)
