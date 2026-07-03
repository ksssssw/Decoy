package com.peekaboo.android

import com.google.gson.Gson
import com.peekaboo.core.MockRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockRuleSanitizerTest {

    private val gson = Gson()

    @Test
    fun `gson-created rule with missing fields gets safe defaults`() {
        // Gson bypasses the constructor, so non-null fields silently hold null
        val raw = gson.fromJson("{}", MockRule::class.java)
        val rule = raw.sanitized()

        assertEquals("", rule.id)
        assertEquals("", rule.urlPattern)
        assertEquals("*", rule.method)
        assertEquals("", rule.responseBody)
        assertEquals(emptyMap(), rule.responseHeaders)
        assertEquals("", rule.description)
        assertEquals("", rule.group)
        assertTrue(rule.createdAt > 0, "createdAt must be backfilled")
    }

    @Test
    fun `provided values are preserved`() {
        val json = """{"id":"r1","urlPattern":"/posts","method":"GET","statusCode":404,
            "responseBody":"{}","delayMs":300,"isEnabled":false,"createdAt":123,"group":"g"}"""
        val rule = gson.fromJson(json, MockRule::class.java).sanitized()

        assertEquals("r1", rule.id)
        assertEquals("/posts", rule.urlPattern)
        assertEquals("GET", rule.method)
        assertEquals(404, rule.statusCode)
        assertEquals(300L, rule.delayMs)
        assertEquals(false, rule.isEnabled)
        assertEquals(123L, rule.createdAt)
        assertEquals("g", rule.group)
    }

    @Test
    fun `zero createdAt is replaced with a real timestamp`() {
        val rule = gson.fromJson("""{"createdAt":0}""", MockRule::class.java).sanitized()
        assertTrue(rule.createdAt > 0)
    }

    @Test
    fun `copy() works after sanitizing a gson-created rule`() {
        // Without sanitized(), copy() on a Gson-instantiated rule throws NPE
        val rule = gson.fromJson("{}", MockRule::class.java).sanitized()
        val copied = rule.copy(isEnabled = false)
        assertEquals(false, copied.isEnabled)
    }
}
