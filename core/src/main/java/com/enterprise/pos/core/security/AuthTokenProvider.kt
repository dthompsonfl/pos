package com.enterprise.pos.core.security

import com.enterprise.pos.core.Result

/**
 * Provides auth tokens for backend API calls.
 * Implementations must store tokens securely (encrypted at rest).
 *
 * The token lifecycle is:
 * 1. Login flow writes the token via a concrete implementation.
 * 2. Network layers read the token via [getToken].
 * 3. On 401 responses, the caller invokes [refreshToken] to rotate.
 * 4. Logout calls [clearToken] to wipe the stored credential.
 */
interface AuthTokenProvider {
    /** Returns the current auth token, or null if not authenticated. */
    fun getToken(): String?

    /** Refreshes the token from the backend. Returns the new token on success. */
    suspend fun refreshToken(): Result<String>

    /** Clears the stored token (logout). */
    fun clearToken()
}
