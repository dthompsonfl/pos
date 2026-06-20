package com.enterprise.pos.core

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption manager with per-key-alias key derivation.
 * Used for encrypting sensitive data at rest (PINs, tokens, PII).
 * Pure JVM implementation for unit-testability.
 */
class EncryptionManager {

    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val keyCache = mutableMapOf<String, SecretKeySpec>()

    fun encrypt(plaintext: String, keyAlias: String = "default"): String {
        val key = getOrCreateKey(keyAlias)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(ciphertext: String, keyAlias: String = "default"): String {
        val key = keyCache[keyAlias] ?: throw IllegalArgumentException("Key alias not found: $keyAlias")
        val combined = Base64.getDecoder().decode(ciphertext)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(alias: String): SecretKeySpec {
        return keyCache.getOrPut(alias) {
            val keyBytes = alias.repeat(32).toByteArray(Charsets.UTF_8).copyOf(32)
            SecretKeySpec(keyBytes, "AES")
        }
    }
}
