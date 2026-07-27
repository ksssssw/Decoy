package com.decoy.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeaderRedactorTest {

    @Test
    fun `masks credential headers case-insensitively`() {
        val redacted = HeaderRedactor.redact(
            mapOf(
                "Authorization" to "Bearer secret-token",
                "PROXY-AUTHORIZATION" to "Basic abc",
                "cookie" to "session=1234",
                "Set-Cookie" to "session=1234; HttpOnly",
            )
        )
        assertEquals(HeaderRedactor.MASK, redacted["Authorization"])
        assertEquals(HeaderRedactor.MASK, redacted["PROXY-AUTHORIZATION"])
        assertEquals(HeaderRedactor.MASK, redacted["cookie"])
        assertEquals(HeaderRedactor.MASK, redacted["Set-Cookie"])
    }

    @Test
    fun `leaves other headers untouched and preserves key casing`() {
        val original = mapOf(
            "Content-Type" to "application/json",
            "X-Request-Id" to "42",
        )
        assertEquals(original, HeaderRedactor.redact(original))
    }

    @Test
    fun `empty map stays empty`() {
        assertEquals(emptyMap(), HeaderRedactor.redact(emptyMap()))
    }

    @Test
    fun `masks common api key and token headers`() {
        val redacted = HeaderRedactor.redact(
            mapOf(
                "X-Api-Key" to "k",
                "api-key" to "k",
                "X-Auth-Token" to "t",
                "X-Access-Token" to "t",
                "X-CSRF-Token" to "t",
            )
        )
        assertTrue(redacted.values.all { it == HeaderRedactor.MASK }, "all should be masked: $redacted")
    }

    @Test
    fun `masks vendor headers by credential-like suffix`() {
        val redacted = HeaderRedactor.redact(
            mapOf(
                "X-Client-Token" to "t",
                "X-Webhook-Secret" to "s",
                "X-Internal-Auth" to "a",
                "X-Signing-Key" to "k",
            )
        )
        assertTrue(redacted.values.all { it == HeaderRedactor.MASK }, "all should be masked: $redacted")
    }

    @Test
    fun `non-credential headers with similar words survive`() {
        val original = mapOf(
            "X-Request-Id" to "42",
            "Accept" to "application/json",
            "X-Keyboard-Layout" to "qwerty", // contains "key" but not as a suffix
        )
        assertEquals(original, HeaderRedactor.redact(original))
    }
}
