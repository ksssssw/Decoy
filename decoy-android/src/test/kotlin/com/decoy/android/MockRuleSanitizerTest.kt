package com.decoy.android

import com.google.gson.Gson
import com.decoy.core.MockRule
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

    @Test
    fun `null and invalid header entries are dropped`() {
        // Gson bypasses generics too: Map<String, String> can hold null values
        val rule = gson.fromJson(
            """{"responseHeaders":{"X-Ok":"fine","X-Null":null,"X-Bad-Value":"줄","bad name":"v","":"v"}}""",
            MockRule::class.java,
        ).sanitized()
        assertEquals(mapOf("X-Ok" to "fine"), rule.responseHeaders)
    }

    @Test
    fun `out-of-range statusCode falls back to 200`() {
        assertEquals(200, gson.fromJson("""{"statusCode":42}""", MockRule::class.java).sanitized().statusCode)
        assertEquals(200, gson.fromJson("""{"statusCode":9999}""", MockRule::class.java).sanitized().statusCode)
        assertEquals(503, gson.fromJson("""{"statusCode":503}""", MockRule::class.java).sanitized().statusCode)
    }

    @Test
    fun `delayMs is clamped into range`() {
        assertEquals(0L, gson.fromJson("""{"delayMs":-5}""", MockRule::class.java).sanitized().delayMs)
        assertEquals(60_000L, gson.fromJson("""{"delayMs":999999999}""", MockRule::class.java).sanitized().delayMs)
        assertEquals(300L, gson.fromJson("""{"delayMs":300}""", MockRule::class.java).sanitized().delayMs)
    }

    @Test
    fun `header validators accept the RFC token charset and printable values`() {
        assertTrue(isValidHeaderName("X-Api-Key"))
        assertTrue(isValidHeaderName("Content-Type"))
        assertTrue(!isValidHeaderName(""))
        assertTrue(!isValidHeaderName("X Test"))       // space
        assertTrue(!isValidHeaderName("X:Test"))       // delimiter
        assertTrue(!isValidHeaderName("한글"))          // non-ASCII

        assertTrue(isValidHeaderValue("application/json; charset=utf-8"))
        assertTrue(isValidHeaderValue("tab\tis fine"))
        assertTrue(!isValidHeaderValue("line\nbreak"))
        assertTrue(!isValidHeaderValue("héllo"))       // non-ASCII
    }
}
