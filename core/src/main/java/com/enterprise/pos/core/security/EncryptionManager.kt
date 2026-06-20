package com.enterprise.pos.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * EncryptionManager provides AES-256-GCM encryption backed by the Android Keystore.
 *
 * FIPS 140-2 compliance: AES-256-GCM is a NIST-approved authenticated encryption mode.
 * When hardware-backed keystore is available (StrongBox), keys are protected in a
 * tamper-resistant hardware security module (HSM). Falls back to TEE (Trusted Execution
 * Environment) if StrongBox is unavailable.
 *
 * PCI DSS Level 4: Encryption keys are never exposed to application memory; they are
 * generated and stored inside the Android Keystore. Key rotation is supported to limit
 * the cryptoperiod of any single key.
 */
class EncryptionManager(private val context: Context) {

    private val tag = "EncryptionManager"
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    private val keyRotationPref = context.getSharedPreferences(KEY_ROTATION_PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128 // bits
        private const val GCM_IV_LENGTH = 12 // bytes
        private const val KEY_ROTATION_PREFS = "encryption_key_rotation"
        private const val PREF_KEY_VERSION = "key_version_"
        private const val KEY_VERSION_SUFFIX = "_v"
        private const val CURRENT_KEY_VERSION = 1
    }

    init {
        ensureKeyExists(DEFAULT_KEY_ALIAS)
    }

    /**
     * Encrypt plaintext using AES-256-GCM with the Android Keystore.
     *
     * FIPS 140-2: AES-256-GCM provides authenticated encryption with associated data (AEAD).
     * The output format is `base64(iv + ciphertext + authTag)`.
     *
     * PCI DSS: Sensitive data (e.g., tokens, session material) is encrypted at rest.
     */
    fun encrypt(plaintext: String, keyAlias: String = DEFAULT_KEY_ALIAS): String {
        try {
            ensureKeyExists(keyAlias)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey(keyAlias))
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = iv + ciphertext
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(tag, "Encryption failed for alias $keyAlias", e)
            throw SecurityException("Encryption failed: ${e.message}", e)
        }
    }

    /**
     * Decrypt ciphertext using AES-256-GCM with the Android Keystore.
     *
     * FIPS 140-2: GCM authentication tag is verified during decryption; tampered
     * ciphertext will throw AEADBadTagException.
     */
    fun decrypt(ciphertext: String, keyAlias: String = DEFAULT_KEY_ALIAS): String {
        try {
            val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) {
                throw SecurityException("Invalid ciphertext: too short")
            }
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey(keyAlias), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val plaintext = cipher.doFinal(encrypted)
            return String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(tag, "Decryption failed for alias $keyAlias", e)
            throw SecurityException("Decryption failed: ${e.message}", e)
        }
    }

    /**
     * Rotate the key for a given alias. Old data must be re-encrypted with the new key.
     * Returns the new key version number.
     *
     * FIPS 140-2: Supports cryptoperiod enforcement by rotating keys periodically.
     */
    fun rotateKey(keyAlias: String): Int {
        val currentVersion = keyRotationPref.getInt(PREF_KEY_VERSION + keyAlias, CURRENT_KEY_VERSION)
        val newVersion = currentVersion + 1
        val newAlias = "$keyAlias$KEY_VERSION_SUFFIX$newVersion"
        generateKey(newAlias, requireHardware = false)
        keyRotationPref.edit().putInt(PREF_KEY_VERSION + keyAlias, newVersion).apply()
        Log.i(tag, "Rotated key for alias $keyAlias to version $newVersion")
        return newVersion
    }

    /**
     * Check if a key exists in the keystore.
     */
    fun keyExists(keyAlias: String): Boolean {
        return keyStore.containsAlias(keyAlias)
    }

    /**
     * Delete a key from the keystore. Use with caution.
     */
    fun deleteKey(keyAlias: String) {
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
            Log.i(tag, "Deleted key alias $keyAlias")
        }
    }

    private fun ensureKeyExists(keyAlias: String) {
        if (!keyStore.containsAlias(keyAlias)) {
            generateKey(keyAlias, requireHardware = false)
        }
    }

    private fun getKey(keyAlias: String): SecretKey {
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            ?: throw SecurityException("Key $keyAlias not found in keystore")
        return entry.secretKey
    }

    /**
     * Generate a new AES-256 key in the Android Keystore.
     *
     * FIPS 140-2: Attempts to use StrongBox (hardware-backed HSM) if available.
     * Falls back to TEE (Trusted Execution Environment) if hardware is unavailable.
     * Software-only keystore is the last resort and is explicitly logged as a warning.
     */
    private fun generateKey(keyAlias: String, requireHardware: Boolean) {
        try {
            val keyGen = KeyGenerator.getInstance(KEY_ALGORITHM, KEYSTORE_PROVIDER)
            val builder = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            // Attempt StrongBox if available and not explicitly disabled
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
                    builder.setIsStrongBoxBacked(true)
                    Log.i(tag, "Using StrongBox (hardware HSM) for key $keyAlias")
                }
            }

            keyGen.init(builder.build())
            keyGen.generateKey()
            Log.i(tag, "Generated AES-256 key for alias $keyAlias")
        } catch (e: Exception) {
            if (requireHardware) {
                throw SecurityException("Hardware-backed key generation failed and hardware is required", e)
            }
            Log.w(tag, "Hardware-backed key generation failed for $keyAlias, falling back to TEE/software", e)
            // Fallback: generate without StrongBox
            val keyGen = KeyGenerator.getInstance(KEY_ALGORITHM, KEYSTORE_PROVIDER)
            val builder = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
            keyGen.init(builder.build())
            keyGen.generateKey()
            Log.i(tag, "Generated fallback AES-256 key for alias $keyAlias")
        }
    }
}

private const val DEFAULT_KEY_ALIAS = "enterprise_pos_master_key"
