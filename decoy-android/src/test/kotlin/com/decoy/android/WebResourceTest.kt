package com.decoy.android

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoke test that the inspector web UI is packaged into the classpath
 * resources and served from `/`. Catches packaging regressions (e.g. the
 * file moving out of `resources/decoy-web/`) that no route test would notice.
 */
class WebResourceTest {

    private fun webTest(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                decoyModule(
                    AppInfo(
                        appName = "Test App",
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

    @Test
    fun `root serves the inspector page`() = webTest {
        val res = client.get("/")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("<title>Decoy Inspector</title>"), "index.html title missing")
    }

    @Test
    fun `served page contains the app shell the JS relies on`() = webTest {
        val body = client.get("/").bodyAsText()
        // Anchors of the UI contract: views, rule modal, and the live regions
        listOf("id=\"view-traffic\"", "id=\"view-rules\"", "id=\"modal\"", "id=\"rule-form\"", "id=\"request-list\"", "id=\"rules-list\"")
            .forEach { anchor -> assertTrue(body.contains(anchor), "expected $anchor in index.html") }
    }
}
