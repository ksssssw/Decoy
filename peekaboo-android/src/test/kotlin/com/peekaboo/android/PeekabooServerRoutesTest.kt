package com.peekaboo.android

import com.google.gson.Gson
import com.peekaboo.core.CapturedRequest
import com.peekaboo.core.MockRepository
import com.peekaboo.core.MockRule
import com.peekaboo.core.NetworkStore
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeekabooServerRoutesTest {

    private val gson = Gson()

    @Before
    fun setUp() {
        NetworkStore.clear()
        MockRepository.replaceAll(emptyList())
    }

    @After
    fun tearDown() {
        NetworkStore.clear()
        MockRepository.replaceAll(emptyList())
    }

    private fun apiTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            peekabooModule(
                AppInfo(
                    packageName = "com.test.app",
                    appVersion = "1.0",
                    versionCode = 1L,
                    deviceModel = "JVM",
                    sdkInt = 34,
                )
            ) { 8090 }
        }
        block()
    }

    private fun capturedCall(id: String) = CapturedRequest(
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

    private fun ruleJson(pattern: String, group: String = "") =
        """{"urlPattern":"${pattern.replace("\\", "\\\\")}","method":"GET","statusCode":200,"responseBody":"{}","group":"$group"}"""

    @Test
    fun `calls endpoint returns the store contents`() = apiTest {
        NetworkStore.add(capturedCall("c1"))
        val body = client.get("/api/calls").bodyAsText()
        val list = gson.fromJson(body, List::class.java)
        assertEquals(1, list.size)
        assertTrue(body.contains("https://api.test/c1"))
    }

    @Test
    fun `calls by id returns 404 when absent`() = apiTest {
        assertEquals(HttpStatusCode.NotFound, client.get("/api/calls/nope").status)
    }

    @Test
    fun `delete calls clears the store`() = apiTest {
        NetworkStore.add(capturedCall("c1"))
        client.delete("/api/calls")
        assertTrue(NetworkStore.getAll().isEmpty())
    }

    @Test
    fun `creating a mock assigns a server-side id`() = apiTest {
        val response = client.post("/api/mocks") {
            contentType(ContentType.Application.Json)
            setBody(ruleJson("/posts"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val created = gson.fromJson(response.bodyAsText(), MockRule::class.java)
        assertTrue(created.id.isNotBlank())
        assertEquals(created.id, MockRepository.getRules().single().id)
    }

    @Test
    fun `creating a mock with an invalid regex returns 400`() = apiTest {
        val response = client.post("/api/mocks") {
            contentType(ContentType.Application.Json)
            setBody(ruleJson("[unclosed"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(MockRepository.getRules().isEmpty())
    }

    @Test
    fun `toggle flips a rule's enabled state`() = apiTest {
        MockRepository.addRule(MockRule("r1", "/posts", "GET", 200, "{}"))
        client.patch("/api/mocks/r1/toggle")
        assertEquals(false, MockRepository.getRules().single().isEnabled)
    }

    @Test
    fun `import merge appends and replace wipes first`() = apiTest {
        MockRepository.addRule(MockRule("existing", "/old", "GET", 200, "{}"))

        client.post("/api/mocks/import") {
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"merge","rules":[${ruleJson("/a")}]}""")
        }
        assertEquals(2, MockRepository.getRules().size)

        client.post("/api/mocks/import") {
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"replace","rules":[${ruleJson("/b")}]}""")
        }
        assertEquals(1, MockRepository.getRules().size)
        assertEquals("/b", MockRepository.getRules().single().urlPattern)
    }

    @Test
    fun `import skips invalid rules and reports counts`() = apiTest {
        val response = client.post("/api/mocks/import") {
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"merge","rules":[${ruleJson("/ok")},${ruleJson("[bad")}]}""")
        }
        val result = gson.fromJson(response.bodyAsText(), Map::class.java)
        assertEquals(1.0, result["imported"])
        assertEquals(1.0, result["skipped"])
    }

    @Test
    fun `layout endpoint reorders matching precedence`() = apiTest {
        MockRepository.addAll(listOf(
            MockRule("a", "/a", "GET", 200, "{}"),
            MockRule("b", "/b", "GET", 200, "{}"),
        ))
        client.put("/api/mocks/layout") {
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"id":"b","group":"g"},{"id":"a","group":""}]}""")
        }
        val rules = MockRepository.getRules()
        assertEquals(listOf("b", "a"), rules.map { it.id })
        assertEquals("g", rules[0].group)
    }

    @Test
    fun `status endpoint reports app info and counts`() = apiTest {
        NetworkStore.add(capturedCall("c1"))
        val status = gson.fromJson(client.get("/api/status").bodyAsText(), Map::class.java)
        assertEquals("com.test.app", status["packageName"])
        assertEquals(8090.0, status["port"])
        assertEquals(1.0, status["callCount"])
    }
}
