package com.enterprise.pos.core.security

import android.content.Context
import android.content.SharedPreferences
import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.NoopLogger
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecureStorage wraps EncryptedSharedPreferences for encrypted key-value storage.
 *
 * PCI DSS Level 4 compliance: Sensitive data (tokens, credentials, session material)
 * is encrypted at rest using AES-256-GCM. The MasterKey is stored in the Android Keystore
 * and never exposed to application code.
 *
 * FIPS 140-2: Uses AES-256-GCM via AndroidX Security library, which delegates to
 * the Android Keystore (hardware-backed when available).
 *
 * Requires dependency: `androidx.security:security-crypto`
 */
class SecureStorage(context: Context) {

    private val logger: Logger = NoopLogger
    private val tag = "SecureStorage"
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        logger.i(tag, "SecureStorage initialized with AES-256-GCM encrypted preferences")
    }

    /**
     * Write an encrypted string value.
     *
     * PCI DSS: Data is encrypted before storage; plaintext never touches disk.
     */
    fun write(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        logger.d(tag, "Wrote encrypted value for key: $key")
    }

    /**
     * Read and decrypt a string value.
     * Returns null if the key does not exist.
     */
    fun read(key: String): String? {
        val value = prefs.getString(key, null)
        logger.d(tag, "Read encrypted value for key: $key (exists=${value != null})")
        return value
    }

    /**
     * Delete a single key-value pair.
     */
    fun delete(key: String) {
        prefs.edit().remove(key).apply()
        logger.d(tag, "Deleted key: $key")
    }

    /**
     * Clear all encrypted values. Use with extreme caution.
     */
    fun clear() {
        prefs.edit().clear().apply()
        logger.w(tag, "Cleared all secure storage entries")
    }

    /**
     * Check if a key exists.
     */
    fun contains(key: String): Boolean = prefs.contains(key)

    /**
     * Write a boolean value (encrypted).
     */
    fun writeBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    /**
     * Read a boolean value (encrypted).
     */
    fun readBoolean(key: String, default: Boolean = false): Boolean {
        return prefs.getBoolean(key, default)
    }

    /**
     * Write a long value (encrypted).
     */
    fun writeLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    /**
     * Read a long value (encrypted).
     */
    fun readLong(key: String, default: Long = 0L): Long {
        return prefs.getLong(key, default)
    }

    companion object {
        private const val PREFS_FILE_NAME = "enterprise_pos_secure"
        private const val MASTER_KEY_ALIAS = "enterprise_pos_master_key"
    }
}
