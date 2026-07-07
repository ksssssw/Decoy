package com.decoy.core

import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MockRepositoryTest {

    private class InMemoryStorage(
        private val initial: List<MockRule> = emptyList(),
    ) : RuleStorage {
        val saved = mutableListOf<List<MockRule>>()
        val saveLatch = CountDownLatch(1)
        override fun load(): List<MockRule> = initial
        override fun save(rules: List<MockRule>) {
            saved.add(rules)
            saveLatch.countDown()
        }
    }

    private fun rule(
        pattern: String,
        method: String = "GET",
        enabled: Boolean = true,
        group: String = "",
        id: String = pattern + method + group,
    ) = MockRule(
        id = id,
        urlPattern = pattern,
        method = method,
        statusCode = 200,
        responseBody = "{}",
        isEnabled = enabled,
        group = group,
    )

    @Test
    fun `concurrent mutations never corrupt state or throw`() {
        MockRepository.replaceAll((0 until 100).map { rule("/p$it", id = "id$it") })

        val threads = listOf(
            Thread { repeat(100) { i -> MockRepository.toggleRule("id$i") } },
            Thread { repeat(100) { i -> MockRepository.removeRule("id$i") } },
            Thread { repeat(50) { MockRepository.setAllEnabled(false) } },
        )
        val failures = mutableListOf<Throwable>()
        threads.forEach { t -> t.setUncaughtExceptionHandler { _, e -> synchronized(failures) { failures.add(e) } } }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }

        assertTrue(failures.isEmpty(), "concurrent mutation threw: ${failures.firstOrNull()}")
        // The remover eventually deletes every id; interleaved toggles are no-ops on gone rules.
        assertTrue(MockRepository.getRules().isEmpty())
    }

    @Before
    fun setUp() {
        // MockRepository is a singleton — attaching a fresh empty storage resets it
        MockRepository.attachStorage(InMemoryStorage())
    }

    @Test
    fun `finds first enabled rule in list order`() {
        MockRepository.addAll(listOf(rule("/posts", id = "top"), rule("/posts", id = "bottom")))
        assertEquals("top", MockRepository.findMatchingRule("https://api.test/posts", "GET")?.id)
    }

    @Test
    fun `disabled rules are skipped`() {
        MockRepository.addAll(listOf(rule("/posts", enabled = false, id = "off"), rule("/posts", id = "on")))
        assertEquals("on", MockRepository.findMatchingRule("https://api.test/posts", "GET")?.id)
    }

    @Test
    fun `wildcard method matches any method`() {
        MockRepository.addRule(rule("/posts", method = "*"))
        assertNotNull(MockRepository.findMatchingRule("https://api.test/posts", "DELETE"))
    }

    @Test
    fun `method match is case insensitive`() {
        MockRepository.addRule(rule("/posts", method = "get"))
        assertNotNull(MockRepository.findMatchingRule("https://api.test/posts", "GET"))
    }

    @Test
    fun `pattern matches anywhere in the url`() {
        MockRepository.addRule(rule("""posts/\d+"""))
        assertNotNull(MockRepository.findMatchingRule("https://api.test/posts/42?x=1", "GET"))
        assertNull(MockRepository.findMatchingRule("https://api.test/posts", "GET"))
    }

    @Test
    fun `invalid regex never matches and never throws`() {
        MockRepository.addRule(rule("[unclosed"))
        assertNull(MockRepository.findMatchingRule("https://api.test/[unclosed", "GET"))
    }

    @Test
    fun `toggleRule flips isEnabled`() {
        MockRepository.addRule(rule("/posts", id = "r1"))
        MockRepository.toggleRule("r1")
        assertFalse(MockRepository.getRules().single().isEnabled)
        MockRepository.toggleRule("r1")
        assertTrue(MockRepository.getRules().single().isEnabled)
    }

    @Test
    fun `applyLayout reorders rules and reassigns groups keeping leftovers at end`() {
        MockRepository.addAll(listOf(rule("/a", id = "a"), rule("/b", id = "b"), rule("/c", id = "c")))
        MockRepository.applyLayout(listOf(RulePlacement("c", "grp"), RulePlacement("a", "")))
        val rules = MockRepository.getRules()
        assertEquals(listOf("c", "a", "b"), rules.map { it.id })
        assertEquals("grp", rules[0].group)
    }

    @Test
    fun `renameGroup moves every rule of the group`() {
        MockRepository.addAll(listOf(rule("/a", group = "old", id = "a"), rule("/b", group = "keep", id = "b")))
        MockRepository.renameGroup("old", "new")
        assertEquals("new", MockRepository.getRules().first { it.id == "a" }.group)
        assertEquals("keep", MockRepository.getRules().first { it.id == "b" }.group)
    }

    @Test
    fun `setGroupEnabled only affects that group`() {
        MockRepository.addAll(listOf(rule("/a", group = "g1", id = "a"), rule("/b", group = "g2", id = "b")))
        MockRepository.setGroupEnabled("g1", false)
        assertFalse(MockRepository.getRules().first { it.id == "a" }.isEnabled)
        assertTrue(MockRepository.getRules().first { it.id == "b" }.isEnabled)
    }

    @Test
    fun `setAllEnabled toggles every rule`() {
        MockRepository.addAll(listOf(rule("/a", id = "a"), rule("/b", group = "g", id = "b")))
        MockRepository.setAllEnabled(false)
        assertTrue(MockRepository.getRules().none { it.isEnabled })
    }

    @Test
    fun `replaceAll replaces and addAll appends`() {
        MockRepository.addRule(rule("/old", id = "old"))
        MockRepository.replaceAll(listOf(rule("/new", id = "new")))
        assertEquals(listOf("new"), MockRepository.getRules().map { it.id })
        MockRepository.addAll(listOf(rule("/extra", id = "extra")))
        assertEquals(listOf("new", "extra"), MockRepository.getRules().map { it.id })
    }

    @Test
    fun `attachStorage loads persisted rules`() {
        MockRepository.attachStorage(InMemoryStorage(listOf(rule("/persisted", id = "p"))))
        assertEquals(listOf("p"), MockRepository.getRules().map { it.id })
    }

    @Test
    fun `mutations persist asynchronously to the attached storage`() {
        val storage = InMemoryStorage()
        MockRepository.attachStorage(storage)
        MockRepository.addRule(rule("/posts", id = "r1"))
        assertTrue(storage.saveLatch.await(2, TimeUnit.SECONDS), "persist was never called")
        assertEquals(listOf("r1"), storage.saved.last().map { it.id })
    }
}
