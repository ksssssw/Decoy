package com.decoy.core

import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkStoreTest {

    private val addedListeners = mutableListOf<(CapturedRequest) -> Unit>()

    private fun listen(listener: (CapturedRequest) -> Unit) {
        addedListeners.add(listener)
        NetworkStore.addListener(listener)
    }

    private fun call(id: String) = CapturedRequest(
        id = id,
        timestamp = 0L,
        method = "GET",
        url = "https://api.test/$id",
        requestHeaders = emptyMap(),
        requestBody = null,
        responseCode = 200,
        responseHeaders = emptyMap(),
        responseBody = "{}",
        durationMs = 1,
    )

    @Before
    fun setUp() = NetworkStore.clear()

    @After
    fun tearDown() {
        addedListeners.forEach { NetworkStore.removeListener(it) }
        NetworkStore.clear()
    }

    @Test
    fun `getAll returns newest first`() {
        NetworkStore.add(call("first"))
        NetworkStore.add(call("second"))
        assertEquals(listOf("second", "first"), NetworkStore.getAll().map { it.id })
    }

    @Test
    fun `ring buffer evicts the oldest entry beyond 500`() {
        repeat(501) { NetworkStore.add(call("c$it")) }
        val all = NetworkStore.getAll()
        assertEquals(500, all.size)
        assertEquals("c500", all.first().id)
        assertNull(NetworkStore.getById("c0"))
    }

    @Test
    fun `getById finds a stored entry`() {
        NetworkStore.add(call("target"))
        assertEquals("https://api.test/target", NetworkStore.getById("target")?.url)
    }

    @Test
    fun `clear empties the store`() {
        NetworkStore.add(call("c1"))
        NetworkStore.clear()
        assertTrue(NetworkStore.getAll().isEmpty())
    }

    @Test
    fun `listener is notified on add`() {
        val received = mutableListOf<String>()
        listen { received.add(it.id) }
        NetworkStore.add(call("c1"))
        assertEquals(listOf("c1"), received)
    }

    @Test
    fun `removed listener is no longer notified`() {
        val received = mutableListOf<String>()
        val listener: (CapturedRequest) -> Unit = { received.add(it.id) }
        NetworkStore.addListener(listener)
        NetworkStore.removeListener(listener)
        NetworkStore.add(call("c1"))
        assertTrue(received.isEmpty())
    }

    @Test
    fun `a throwing listener does not fail the capture or starve later listeners`() {
        val received = mutableListOf<String>()
        listen { throw IllegalStateException("boom") }
        listen { received.add(it.id) }

        NetworkStore.add(call("c1")) // must not throw

        assertEquals(listOf("c1"), received)
        assertEquals(1, NetworkStore.getAll().size)
    }

    @Test
    fun `total byte budget evicts oldest entries long before the count cap`() {
        val megabyte = "x".repeat(1024 * 1024)
        repeat(40) { NetworkStore.add(call("big$it").copy(responseBody = megabyte)) }

        val all = NetworkStore.getAll()
        // ~2 MB heap per entry against a 32 MB budget → far fewer than 40 retained
        assertTrue(all.size < 40, "expected byte-budget eviction, kept ${all.size}")
        assertEquals("big39", all.first().id, "newest entry must survive")
        assertNull(NetworkStore.getById("big0"))
    }

    @Test
    fun `an entry bigger than the whole budget is still kept as the sole entry`() {
        val huge = "x".repeat(33 * 1024 * 1024)
        NetworkStore.add(call("huge").copy(responseBody = huge))
        assertEquals(listOf("huge"), NetworkStore.getAll().map { it.id })
    }

    @Test
    fun `clear resets the byte budget`() {
        val megabyte = "x".repeat(1024 * 1024)
        repeat(20) { NetworkStore.add(call("big$it").copy(responseBody = megabyte)) }
        NetworkStore.clear()

        // If the budget survived clear(), these small entries would be evicted
        repeat(100) { NetworkStore.add(call("small$it")) }
        assertEquals(100, NetworkStore.getAll().size)
    }
}
