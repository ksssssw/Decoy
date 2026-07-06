package com.peekaboo.okhttp

import com.peekaboo.core.NetworkStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression guard for the release-build safety claim: the no-op interceptor
 * must pass traffic through untouched and record nothing.
 */
class PeekabooInterceptorNoopTest {

    @Test
    fun `proceeds untouched and captures nothing`() {
        NetworkStore.clear()
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody("real"))
            val client = OkHttpClient.Builder()
                .addInterceptor(PeekabooInterceptor())
                .build()

            val response = client.newCall(Request.Builder().url(server.url("/x")).build()).execute()

            assertEquals("real", response.body?.string())
            assertEquals(1, server.requestCount)
            assertTrue(NetworkStore.getAll().isEmpty())
        } finally {
            server.shutdown()
        }
    }
}
