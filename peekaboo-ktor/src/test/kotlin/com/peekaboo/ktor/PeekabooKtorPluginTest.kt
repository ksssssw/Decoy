package com.peekaboo.ktor

import com.peekaboo.core.MockRepository
import com.peekaboo.core.MockRule
import com.peekaboo.core.NetworkStore
import com.peekaboo.core.Peekaboo
import com.peekaboo.core.PeekabooProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.gson.gson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PeekabooKtorPluginTest {

    private class FakePeekaboo : Peekaboo {
        override fun start(port: Int) {}
        override fun stop() {}
        override fun isRunning(): Boolean = true
        override fun getPort(): Int = 8090
    }

    private val engineHits = AtomicInteger(0)

    @Before
    fun setUp() {
        PeekabooProvider.instance = FakePeekaboo()
        NetworkStore.clear()
        MockRepository.replaceAll(emptyList())
        engineHits.set(0)
    }

    @After
    fun tearDown() {
        NetworkStore.clear()
        MockRepository.replaceAll(emptyList())
    }

    /** Client whose engine always answers with [body]/[status]/[contentType]. */
    private fun client(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = """{"ok":1}""",
        contentType: String = "application/json",
    ) = HttpClient(MockEngine) {
        engine {
            addHandler {
                engineHits.incrementAndGet()
                respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
            }
        }
        installPeekaboo()
        install(ContentNegotiation) { gson() }
    }

    private fun failingClient() = HttpClient(MockEngine) {
        engine { addHandler { throw IOException("boom") } }
        installPeekaboo()
    }

    private fun mockRule(pattern: String, delayMs: Long = 0) = MockRule(
        id = "test-rule",
        urlPattern = pattern,
        method = "*",
        statusCode = 418,
        responseBody = """{"mocked":true}""",
        delayMs = delayMs,
    )

    @Test
    fun `status-only read is captured`() = runBlocking {
        // Regression: reading only .status (never .body()) must still be recorded
        val status = client().get("https://api.test/posts").status.value

        assertEquals(200, status)
        val captured = NetworkStore.getAll().single()
        assertEquals("https://api.test/posts", captured.url)
        assertEquals(200, captured.responseCode)
        assertEquals("""{"ok":1}""", captured.responseBody)
    }

    @Test
    fun `body deserialization still works after capture`() = runBlocking {
        val parsed: Map<String, Any?> = client().get("https://api.test/posts").body()

        assertEquals(1.0, parsed["ok"])
        assertEquals("""{"ok":1}""", NetworkStore.getAll().single().responseBody)
    }

    @Test
    fun `non-2xx response is captured`() = runBlocking {
        val response = client(status = HttpStatusCode.NotFound, body = """{"error":"nope"}""")
            .get("https://api.test/missing")

        assertEquals(404, response.status.value)
        val captured = NetworkStore.getAll().single()
        assertEquals(404, captured.responseCode)
        assertEquals("""{"error":"nope"}""", captured.responseBody)
    }

    @Test
    fun `engine exception records the error and rethrows`(): Unit = runBlocking {
        assertFailsWith<IOException> { failingClient().get("https://api.test/broken") }

        val captured = NetworkStore.getAll().single()
        assertNotNull(captured.error)
        assertTrue(captured.error!!.contains("boom"))
        assertEquals(null, captured.responseCode)
    }

    @Test
    fun `matching mock rule bypasses the engine`() = runBlocking {
        MockRepository.addRule(mockRule("/posts"))
        val response = client().get("https://api.test/posts")

        assertEquals(418, response.status.value)
        assertEquals("""{"mocked":true}""", response.bodyAsText())
        assertEquals(0, engineHits.get())
        assertTrue(NetworkStore.getAll().single().isMocked)
    }

    @Test
    fun `mocked call records actual elapsed time and the configured delay`() = runBlocking {
        MockRepository.addRule(mockRule("/posts", delayMs = 200))
        client().get("https://api.test/posts").status

        val captured = NetworkStore.getAll().single()
        assertTrue(captured.durationMs >= 200, "expected >=200ms, was ${captured.durationMs}")
        assertEquals(200L, captured.mockDelayMs)
        assertTrue(captured.isMocked)
    }

    @Test
    fun `request body is captured`() = runBlocking {
        client().post("https://api.test/posts") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("title" to "hi"))
        }.status

        assertEquals("""{"title":"hi"}""", NetworkStore.getAll().single().requestBody)
    }

    @Test
    fun `event-stream body is skipped with a marker`() = runBlocking {
        client(body = "data: hi\n\n", contentType = "text/event-stream")
            .get("https://api.test/events").status

        assertEquals("[skipped: event-stream]", NetworkStore.getAll().single().responseBody)
    }

    @Test
    fun `binary body is skipped with a marker`() = runBlocking {
        client(body = "binarybytes", contentType = "application/octet-stream")
            .get("https://api.test/file").status

        assertTrue(NetworkStore.getAll().single().responseBody!!.startsWith("[binary body:"))
    }

    @Test
    fun `exactly one entry is recorded per call even when the body is consumed`() = runBlocking {
        client().get("https://api.test/posts").bodyAsText()

        assertEquals(1, NetworkStore.getAll().size)
    }
}
