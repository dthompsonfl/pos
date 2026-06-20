package com.enterprise.pos.domain.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinHasherTest {

    @Test
    fun `hash produces pbkdf2 format with 4 parts`() {
        val h = PinHasher.hash("1234")
        val parts = h.split('$')
        assertThat(parts).hasSize(4)
        assertThat(parts[0]).isEqualTo("pbkdf2")
        assertThat(parts[1].toInt()).isAtLeast(10_000) // high iteration count
    }

    @Test
    fun `verify returns true for correct PIN`() {
        val h = PinHasher.hash("1234")
        assertThat(PinHasher.verify("1234", h)).isTrue()
    }

    @Test
    fun `verify returns false for wrong PIN`() {
        val h = PinHasher.hash("1234")
        assertThat(PinHasher.verify("9999", h)).isFalse()
    }

    @Test
    fun `verify returns false for empty PIN`() {
        val h = PinHasher.hash("1234")
        assertThat(PinHasher.verify("", h)).isFalse()
    }

    @Test
    fun `verify returns false for malformed hash`() {
        assertThat(PinHasher.verify("1234", "malformed")).isFalse()
        assertThat(PinHasher.verify("1234", "pbkdf2\$abc")).isFalse()
    }

    @Test
    fun `same PIN produces different hashes due to random salt`() {
        val h1 = PinHasher.hash("1234")
        val h2 = PinHasher.hash("1234")
        assertThat(h1).isNotEqualTo(h2) // different salts
        assertThat(PinHasher.verify("1234", h1)).isTrue()
        assertThat(PinHasher.verify("1234", h2)).isTrue()
    }

    @Test
    fun `hash rejects too-short PIN`() {
        try {
            PinHasher.hash("123")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `hash rejects too-long PIN`() {
        try {
            PinHasher.hash("1234567")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `hash rejects non-digit PIN`() {
        try {
            PinHasher.hash("12a4")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}

class LoginAttemptLimiterTest {

    private var fakeNow = 0L
    private val limiter = LoginAttemptLimiter(
        maxAttempts = 3,
        lockoutMs = 5 * 60 * 1000L,
        windowMs = 15 * 60 * 1000L,
        now = { fakeNow }
    )

    @Test
    fun `can attempt initially`() {
        assertThat(limiter.canAttempt("emp-1")).isTrue()
    }

    @Test
    fun `locks after max failed attempts`() {
        limiter.recordFailure("emp-1")
        limiter.recordFailure("emp-1")
        assertThat(limiter.canAttempt("emp-1")).isTrue() // 2 of 3
        limiter.recordFailure("emp-1")
        assertThat(limiter.canAttempt("emp-1")).isFalse() // locked
    }

    @Test
    fun `locked employee has non-zero msUntilUnlock`() {
        limiter.recordFailure("emp-1")
        limiter.recordFailure("emp-1")
        limiter.recordFailure("emp-1")
        assertThat(limiter.msUntilUnlock("emp-1")).isGreaterThan(0L)
    }

    @Test
    fun `successful login resets counter`() {
        limiter.recordFailure("emp-1")
        limiter.recordFailure("emp-1")
        limiter.recordSuccess("emp-1")
        // Should be able to attempt again
        assertThat(limiter.canAttempt("emp-1")).isTrue()
        // Counter reset — needs 3 more failures to lock
        limiter.recordFailure("emp-1")
        assertThat(limiter.canAttempt("emp-1")).isTrue()
    }

    @Test
    fun `lockout expires after lockoutMs`() {
        limiter.recordFailure("emp-1")
        limiter.recordFailure("emp-1")
        limiter.recordFailure("emp-1")
        assertThat(limiter.canAttempt("emp-1")).isFalse()
        // Advance time past lockout
        fakeNow += 6 * 60 * 1000L
        assertThat(limiter.canAttempt("emp-1")).isTrue()
    }

    @Test
    fun `different employees tracked independently`() {
        limiter.recordFailure("emp-1")
        limiter.recordFailure("emp-1")
        limiter.recordFailure("emp-1")
        assertThat(limiter.canAttempt("emp-1")).isFalse()
        assertThat(limiter.canAttempt("emp-2")).isTrue()
    }
}
