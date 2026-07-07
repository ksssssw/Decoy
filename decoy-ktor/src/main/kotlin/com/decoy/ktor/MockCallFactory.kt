@file:OptIn(io.ktor.utils.io.InternalAPI::class)

package com.decoy.ktor

// ── Ktor 3.x ─────────────────────────────────────────────────────────────────
// Ktor's public API offers no way to fabricate an HttpClientCall, so this file
// is the single place that touches @InternalAPI (HttpResponseData constructor).
// A Ktor major-version bump changes only this file (in 3.0, InternalAPI moved
// from io.ktor.util to io.ktor.utils.io; the constructors below were unchanged).

import com.decoy.core.MockRule
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
            // Default only — a Content-Type in the rule's headers must not end up
            // duplicated alongside the fallback (append, not set, below).
            if (rule.responseHeaders.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                append(HttpHeaders.ContentType, "application/json")
            }
            rule.responseHeaders.forEach { (k, v) -> append(k, v) }
        },
        version = HttpProtocolVersion.HTTP_1_1,
        body = ByteReadChannel(rule.responseBody.toByteArray()),
        callContext = currentCoroutineContext() + Job(currentCoroutineContext()[Job]),
    )
    return HttpClientCall(client, requestData, responseData)
}
