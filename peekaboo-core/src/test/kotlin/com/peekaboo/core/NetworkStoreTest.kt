package com.peekaboo.core

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
}
