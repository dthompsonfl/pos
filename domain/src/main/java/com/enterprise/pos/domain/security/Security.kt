package com.enterprise.pos.domain.security

import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.model.RolePermissions
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Secure PIN hashing using PBKDF2 with HMAC-SHA256.
 *
 * PINs are short (4-6 digits), so we use:
 *  - A high iteration count (100,000) to slow brute-force
 *  - A per-PIN random 16-byte salt
 *  - Output encoded as `pbkdf2$iterations$saltHex$hashHex`
 *
 * Verification uses a constant-time comparison to prevent timing attacks.
 *
 * NOTE: This file lives in :core so that it has no Android dependencies and can be
 * unit-tested in plain JVM. The :domain module (where EmployeeRole lives) is also pure
 * Kotlin, so this compiles.
 */
object PinHasher {

    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256 // bits
    private const val SALT_LENGTH = 16 // bytes
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    /** Hash a PIN with a fresh random salt. Returns `pbkdf2$iterations$saltHex$hashHex`. */
    fun hash(pin: String): String {
        require(pin.length in 4..6) { "PIN must be 4-6 digits" }
        require(pin.all { it.isDigit() }) { "PIN must be digits only" }
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = deriveKey(pin, salt, ITERATIONS)
        return "pbkdf2\$$ITERATIONS\$${salt.toHex()}\$${hash.toHex()}"
    }

    /** Verify a PIN against a stored hash. Constant-time comparison. */
    fun verify(pin: String, storedHash: String): Boolean {
        val parts = storedHash.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = parts[2].fromHex() ?: return false
        val expectedHash = parts[3].fromHex() ?: return false
        val actualHash = deriveKey(pin, salt, iterations)
        return constantTimeEquals(expectedHash, actualHash)
    }

    private fun deriveKey(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching {
            ByteArray(length / 2) { i ->
                ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
            }
        }.getOrNull()
    }
}

/**
 * Failed-login lockout tracker — in-memory per-employee.
 *
 * After [maxAttempts] failed attempts within [windowMs], the account is locked for [lockoutMs].
 * Successful login resets the counter.
 *
 * In production, persist to Room so lockout survives app restart.
 */
class LoginAttemptLimiter(
    private val maxAttempts: Int = 5,
    private val lockoutMs: Long = 5 * 60 * 1000L, // 5 minutes
    private val windowMs: Long = 15 * 60 * 1000L, // 15 minute sliding window
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private data class AttemptState(var failedAttempts: Int, var firstAttemptAt: Long, var lockedUntil: Long)

    private val state = mutableMapOf<String, AttemptState>() // employeeId -> state

    @Synchronized
    fun canAttempt(employeeId: String): Boolean {
        val s = state[employeeId] ?: return true
        val n = now()
        if (n < s.lockedUntil) return false
        // Reset window if expired
        if (n - s.firstAttemptAt > windowMs) {
            s.failedAttempts = 0
            s.firstAttemptAt = n
            s.lockedUntil = 0L
        }
        return true
    }

    @Synchronized
    fun recordFailure(employeeId: String) {
        val n = now()
        val s = state.getOrPut(employeeId) { AttemptState(0, n, 0L) }
        if (n - s.firstAttemptAt > windowMs) {
            s.failedAttempts = 0
            s.firstAttemptAt = n
        }
        s.failedAttempts++
        if (s.failedAttempts >= maxAttempts) {
            s.lockedUntil = n + lockoutMs
        }
    }

    @Synchronized
    fun recordSuccess(employeeId: String) {
        state.remove(employeeId)
    }

    @Synchronized
    fun msUntilUnlock(employeeId: String): Long {
        val s = state[employeeId] ?: return 0L
        val n = now()
        return (s.lockedUntil - n).coerceAtLeast(0L)
    }
}

/**
 * Employee session — represents the currently authenticated employee on this register.
 * Created on successful login, cleared on logout or session expiry.
 */
data class EmployeeSession(
    val employeeId: EmployeeId,
    val employeeName: String,
    val role: EmployeeRole,
    val storeId: StoreId,
    val registerId: RegisterId,
    val startedAt: Long,
    val expiresAt: Long,
    val permissions: RolePermissions
) {
    val isActive: Boolean get() = System.currentTimeMillis() < expiresAt

    fun hasPermission(check: RolePermissions.() -> Boolean): Boolean =
        isActive && permissions.check()
}

/**
 * Manager override — when an employee without sufficient permissions needs to perform
 * a protected action, a manager authenticates to grant a one-shot override.
 */
data class ManagerOverride(
    val managerId: EmployeeId,
    val managerName: String,
    val targetAction: String,
    val reason: String,
    val grantedAt: Long,
    val expiresAt: Long
) {
    val isActive: Boolean get() = System.currentTimeMillis() < expiresAt
}
