@file:OptIn(io.ktor.util.InternalAPI::class)

package com.peekaboo.ktor

// ── Ktor 2.x ONLY ─────────────────────────────────────────────────────────────
// Ktor's public API offers no way to fabricate an HttpClientCall, so this file
// is the single place that touches @InternalAPI (HttpResponseData constructor).
// When migrating to Ktor 3.x, only this file should need changes.

import com.peekaboo.core.MockRule
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/** Builds a fake [HttpClientCall] carrying the mock rule's response — no network involved. */
internal suspend fun createMockCall(
    client: HttpClient,
    requestData: HttpRequestData,
    rule: MockRule,
): HttpClientCall {
    val responseData = HttpResponseData(
        statusCode = HttpStatusCode.fromValue(rule.statusCode),
        requestTime = GMTDate(),
        headers = Headers.build {
            append(HttpHeaders.ContentType, "application/json")
            rule.responseHeaders.forEach { (k, v) -> append(k, v) }
        },
        version = HttpProtocolVersion.HTTP_1_1,
        body = ByteReadChannel(rule.responseBody.toByteArray()),
        callContext = currentCoroutineContext() + Job(currentCoroutineContext()[Job]),
    )
    return HttpClientCall(client, requestData, responseData)
}
