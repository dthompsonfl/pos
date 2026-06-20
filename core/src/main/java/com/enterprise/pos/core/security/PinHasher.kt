package com.enterprise.pos.core.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Secure PIN hashing using PBKDF2 with HMAC-SHA256.
 *
 * PCI DSS Level 4 compliance: PINs must never be stored in plaintext.
 * We use PBKDF2 (NIST FIPS 140-2 approved algorithm) with:
 *  - 100,000 iterations to slow brute-force attacks
 *  - Per-PIN random 16-byte salt
 *  - 256-bit derived key length
 *  - Constant-time comparison to prevent timing side-channels
 *
 * Output format: `pbkdf2$iterations$saltHex$hashHex`
 */
object PinHasher {

    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256 // bits
    private const val SALT_LENGTH = 16 // bytes
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    /**
     * Hash a PIN with a fresh random salt.
     * Returns `pbkdf2$iterations$saltHex$hashHex`.
     *
     * FIPS 140-2: PBKDF2WithHmacSHA256 is a NIST-approved KDF.
     * PCI DSS: PINs are hashed, never stored or transmitted in plaintext.
     */
    fun hashPin(pin: String): String {
        require(pin.length in 4..8) { "PIN must be 4-8 digits" }
        require(pin.all { it.isDigit() }) { "PIN must be digits only" }
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = deriveKey(pin, salt, ITERATIONS)
        return "pbkdf2\$$ITERATIONS\$${salt.toHex()}\$${hash.toHex()}"
    }

    /**
     * Verify a PIN against a stored hash.
     * Uses constant-time comparison to prevent timing attacks.
     *
     * FIPS 140-2: Uses approved HMAC-SHA256 underlying PRF.
     * PCI DSS: Verifies credentials without exposing the stored hash.
     */
    fun verifyPin(pin: String, hash: String): Boolean {
        val parts = hash.split('$')
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
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
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
