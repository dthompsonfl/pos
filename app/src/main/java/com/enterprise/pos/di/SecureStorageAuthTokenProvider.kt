package com.enterprise.pos.di

import android.content.Context
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.security.AuthTokenProvider
import com.enterprise.pos.core.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [AuthTokenProvider] backed by [SecureStorage] (AES-256-GCM encrypted preferences).
 *
 * Token is read from encrypted storage under key "auth_token".
 * If no token exists, [getToken] returns null.
 *
 * [refreshToken] is a stub that must be wired to your auth backend's refresh endpoint
 * (e.g., POST /v1/auth/refresh). Until wired, it returns a structured failure so callers
 * can fall back to re-authentication.
 */
@Singleton
class SecureStorageAuthTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : AuthTokenProvider {

    private val secureStorage = SecureStorage(context)

    override fun getToken(): String? = secureStorage.read(KEY_AUTH_TOKEN)

    override suspend fun refreshToken(): Result<String> {
        // This implementation does not have a configured refresh endpoint.
        // The caller should navigate to login when this returns failure.
        return Result.failure(
            IllegalStateException(
                "Token refresh not configured. " +
                    "Wire SecureStorageAuthTokenProvider.refreshToken to your auth backend."
            )
        )
    }

    override fun clearToken() {
        secureStorage.delete(KEY_AUTH_TOKEN)
    }

    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
    }
}
