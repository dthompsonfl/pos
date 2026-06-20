package com.enterprise.pos.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.KeyStore
import javax.crypto.Cipher

class EncryptionManagerTest {

    @Test
    fun `encrypt decrypt roundtrip succeeds`() {
        val manager = EncryptionManager()
        val plaintext = "sensitive order data"
        val encrypted = manager.encrypt(plaintext)
        assertThat(encrypted).isNotEqualTo(plaintext)
        val decrypted = manager.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `encrypt produces different output for same input`() {
        val manager = EncryptionManager()
        val plaintext = "same text"
        val encrypted1 = manager.encrypt(plaintext)
        val encrypted2 = manager.encrypt(plaintext)
        assertThat(encrypted1).isNotEqualTo(encrypted2)
    }

    @Test
    fun `decrypt with wrong key alias fails`() {
        val manager = EncryptionManager()
        val encrypted = manager.encrypt("test", keyAlias = "alias_a")
        val result = runCatching { manager.decrypt(encrypted, keyAlias = "alias_b") }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `encrypt with different key aliases produces different results`() {
        val manager = EncryptionManager()
        val encryptedA = manager.encrypt("test", keyAlias = "alias_a")
        val encryptedB = manager.encrypt("test", keyAlias = "alias_b")
        assertThat(encryptedA).isNotEqualTo(encryptedB)
    }

    @Test
    fun `empty string roundtrip`() {
        val manager = EncryptionManager()
        val encrypted = manager.encrypt("")
        val decrypted = manager.decrypt(encrypted)
        assertThat(decrypted).isEqualTo("")
    }

    @Test
    fun `large data roundtrip`() {
        val manager = EncryptionManager()
        val large = "a".repeat(10000)
        val encrypted = manager.encrypt(large)
        val decrypted = manager.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(large)
    }

    @Test
    fun `unicode roundtrip`() {
        val manager = EncryptionManager()
        val unicode = "Hello \u4e16\u754c \ud83c\udf0d"
        val encrypted = manager.encrypt(unicode)
        val decrypted = manager.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(unicode)
    }

    @Test
    fun `decrypt invalid base64 fails`() {
        val manager = EncryptionManager()
        val result = runCatching { manager.decrypt("not-valid-base64!!!") }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `decrypt tampered ciphertext fails`() {
        val manager = EncryptionManager()
        val encrypted = manager.encrypt("test")
        val tampered = encrypted.dropLast(1) + "x"
        val result = runCatching { manager.decrypt(tampered) }
        assertThat(result.isFailure).isTrue()
    }
}
