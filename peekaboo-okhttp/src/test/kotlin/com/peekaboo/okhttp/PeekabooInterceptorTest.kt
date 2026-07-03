package com.peekaboo.okhttp

import com.peekaboo.core.MockRepository
import com.peekaboo.core.MockRule
import com.peekaboo.core.NetworkStore
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

class PeekabooInterceptorTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .addInterceptor(PeekabooInterceptor())
        .connectTimeout(2, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
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
