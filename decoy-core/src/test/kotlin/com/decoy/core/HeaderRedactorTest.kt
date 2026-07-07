package com.decoy.core

import org.junit.Test
import kotlin.test.assertEquals

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
}
