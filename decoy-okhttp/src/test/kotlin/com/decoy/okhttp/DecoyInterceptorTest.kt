package com.decoy.okhttp

import com.decoy.core.MockRepository
import com.decoy.core.MockRule
import com.decoy.core.NetworkStore
import com.decoy.core.Decoy
import com.decoy.core.DecoyProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DecoyInterceptorTest {

    private class FakeDecoy(private val running: Boolean = true) : Decoy {
        override fun start(port: Int) {}
        override fun stop() {}
        override fun isRunning(): Boolean = running
        override fun getPort(): Int = 8090
    }

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .addInterceptor(DecoyInterceptor())
        .connectTimeout(2, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        DecoyProvider.instance = FakeDecoy()
        NetworkStore.clear()
        MockRepository.replaceAll(emptyList())
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        NetworkStore.clear()
        MockRepository.replaceAll(emptyList())
    }

    private fun mockRule(pattern: String, delayMs: Long = 0) = MockRule(
        id = "test-rule",
        urlPattern = pattern,
        method = "*",
        statusCode = 418,
        responseBody = """{"mocked":true}""",
        delayMs = delayMs,
    )

    private fun execute(request: Request) = client.newCall(request).execute()

    @Test
    fun `real call is captured with method url status and body`() {
        server.enqueue(MockResponse().setBody("""{"ok":1}""").setHeader("Content-Type", "application/json"))
        execute(Request.Builder().url(server.url("/posts")).build()).close()

        val captured = NetworkStore.getAll().single()
        assertEquals("GET", captured.method)
        assertTrue(captured.url.endsWith("/posts"))
        assertEquals(200, captured.responseCode)
        assertEquals("""{"ok":1}""", captured.responseBody)
        assertTrue(captured.durationMs >= 0)
        assertEquals(false, captured.isMocked)
    }

    @Test
    fun `404 response is captured`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"nope"}""")
            .setHeader("Content-Type", "application/json"))
        execute(Request.Builder().url(server.url("/missing")).build()).close()

        val captured = NetworkStore.getAll().single()
        assertEquals(404, captured.responseCode)
        assertEquals("""{"error":"nope"}""", captured.responseBody)
    }

    @Test
    fun `caller can still read the body after capture`() {
        server.enqueue(MockResponse().setBody("""{"ok":1}""").setHeader("Content-Type", "application/json"))
        val response = execute(Request.Builder().url(server.url("/posts")).build())
        assertEquals("""{"ok":1}""", response.body?.string())
    }

    @Test
    fun `request body is captured`() {
        server.enqueue(MockResponse().setBody("{}"))
        val body = """{"title":"hi"}""".toRequestBody("application/json".toMediaType())
        execute(Request.Builder().url(server.url("/posts")).post(body).build()).close()

        assertEquals("""{"title":"hi"}""", NetworkStore.getAll().single().requestBody)
    }

    @Test
    fun `matching mock rule short-circuits the network`() {
        MockRepository.addRule(mockRule("/posts"))
        val response = execute(Request.Builder().url(server.url("/posts")).build())

        assertEquals(418, response.code)
        assertEquals("""{"mocked":true}""", response.body?.string())
        assertEquals(0, server.requestCount)
        assertTrue(NetworkStore.getAll().single().isMocked)
    }

    @Test
    fun `mocked call records actual elapsed time and the configured delay`() {
        MockRepository.addRule(mockRule("/posts", delayMs = 200))
        execute(Request.Builder().url(server.url("/posts")).build()).close()

        val captured = NetworkStore.getAll().single()
        assertTrue(captured.durationMs >= 200, "expected >=200ms, was ${captured.durationMs}")
        assertEquals(200L, captured.mockDelayMs)
        assertTrue(captured.isMocked)
    }

    @Test
    fun `connection failure records the error and rethrows`() {
        assertFailsWith<IOException> {
            execute(Request.Builder().url("http://127.0.0.1:1/unreachable").build())
        }
        val captured = NetworkStore.getAll().single()
        assertNotNull(captured.error)
        assertEquals(null, captured.responseCode)
    }

    @Test
    fun `binary body is skipped with a marker`() {
        server.enqueue(MockResponse().setBody("binarybytes").setHeader("Content-Type", "application/octet-stream"))
        execute(Request.Builder().url(server.url("/file")).build()).close()

        assertTrue(NetworkStore.getAll().single().responseBody!!.startsWith("[binary body:"))
    }

    @Test
    fun `event-stream body is skipped with a marker`() {
        server.enqueue(MockResponse().setBody("data: hi\n\n").setHeader("Content-Type", "text/event-stream"))
        execute(Request.Builder().url(server.url("/events")).build()).close()

        assertEquals("[skipped: event-stream]", NetworkStore.getAll().single().responseBody)
    }

    @Test
    fun `stays fully passive when the inspector is not running`() {
        DecoyProvider.instance = FakeDecoy(running = false)
        MockRepository.addRule(mockRule("/posts"))
        server.enqueue(MockResponse().setBody("real"))

        val response = execute(Request.Builder().url(server.url("/posts")).build())

        assertEquals("real", response.body?.string())
        assertEquals(1, server.requestCount) // mock rule ignored — real network used
        assertTrue(NetworkStore.getAll().isEmpty()) // nothing captured either
    }

    @Test
    fun `response body over 1MB is truncated at the limit and flagged`() {
        val limit = 1024 * 1024
        server.enqueue(MockResponse().setBody("a".repeat(limit + 1))
            .setHeader("Content-Type", "application/json"))
        execute(Request.Builder().url(server.url("/big")).build()).close()

        val captured = NetworkStore.getAll().single()
        assertTrue(captured.bodyTruncated)
        assertEquals(limit, captured.responseBody!!.length)
    }

    @Test
    fun `response body exactly at 1MB is captured whole without the flag`() {
        val limit = 1024 * 1024
        server.enqueue(MockResponse().setBody("a".repeat(limit))
            .setHeader("Content-Type", "application/json"))
        execute(Request.Builder().url(server.url("/exact")).build()).close()

        val captured = NetworkStore.getAll().single()
        assertEquals(false, captured.bodyTruncated)
        assertEquals(limit, captured.responseBody!!.length)
    }

    @Test
    fun `request body over 1MB is skipped without buffering`() {
        server.enqueue(MockResponse().setBody("{}"))
        val big = "a".repeat(1024 * 1024 + 1).toRequestBody("application/json".toMediaType())
        execute(Request.Builder().url(server.url("/upload")).post(big).build()).close()

        assertTrue(NetworkStore.getAll().single().requestBody!!.startsWith("[skipped: request body"))
    }

    @Test
    fun `credential headers are redacted in captured request and response`() {
        server.enqueue(MockResponse().setBody("{}")
            .setHeader("Content-Type", "application/json")
            .setHeader("Set-Cookie", "session=1234"))
        execute(Request.Builder().url(server.url("/auth"))
            .header("Authorization", "Bearer secret")
            .header("X-Request-Id", "42")
            .build()).close()

        val captured = NetworkStore.getAll().single()
        assertEquals("[redacted]", captured.requestHeaders["Authorization"])
        assertEquals("42", captured.requestHeaders["X-Request-Id"])
        assertEquals("[redacted]", captured.responseHeaders["Set-Cookie"])
    }

    @Test
    fun `still-compressed body is skipped with a marker`() {
        server.enqueue(MockResponse().setBody("not-really-gzip")
            .setHeader("Content-Type", "application/json")
            .setHeader("Content-Encoding", "gzip"))
        // Explicit Accept-Encoding disables OkHttp's transparent decompression,
        // so the interceptor sees the Content-Encoding header and must skip.
        execute(Request.Builder().url(server.url("/gz")).header("Accept-Encoding", "gzip").build()).close()

        assertEquals("[skipped: content-encoding gzip]", NetworkStore.getAll().single().responseBody)
    }
}
