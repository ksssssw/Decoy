@file:OptIn(io.ktor.util.InternalAPI::class)

package com.peekaboo.ktor

import com.peekaboo.core.CapturedRequest
import com.peekaboo.core.MockRepository
import com.peekaboo.core.NetworkStore
import com.peekaboo.core.PeekabooProvider
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.client.statement.HttpResponseContainer
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import java.util.UUID

private val PeekabooStartTime = AttributeKey<Long>("PeekabooStartTime")
private val PeekabooRequestId = AttributeKey<String>("PeekabooRequestId")
private val PeekabooIsMocked = AttributeKey<Boolean>("PeekabooIsMocked")

/**
 * Ktor client plugin that captures HTTP requests/responses into Peekaboo's NetworkStore
 * and applies mock rules registered via the web UI.
 *
 * Install this plugin BEFORE ContentNegotiation:
 * ```
 * HttpClient(CIO) {
 *     installPeekaboo()
 *     install(ContentNegotiation) { gson() }
 * }
 * ```
 */
val PeekabooKtorPlugin = createClientPlugin("PeekabooPlugin") {

    onRequest { request, _ ->
        request.attributes.put(PeekabooStartTime, System.currentTimeMillis())
        request.attributes.put(PeekabooRequestId, UUID.randomUUID().toString())
    }

    // Mock interception — runs before the engine sends the request.
    // If a matching MockRule exists, a fake HttpClientCall is returned immediately
    // without touching the network.
    client.plugin(HttpSend).intercept { requestBuilder ->
        if (!PeekabooProvider.isInitialized() || !PeekabooProvider.instance.isRunning()) {
            return@intercept execute(requestBuilder)
        }

        val url = requestBuilder.url.buildString()
        val method = requestBuilder.method.value
        val mockRule = MockRepository.findMatchingRule(url, method)

        if (mockRule != null) {
            if (mockRule.delayMs > 0) delay(mockRule.delayMs)

            val startTime = requestBuilder.attributes.getOrNull(PeekabooStartTime)
                ?: System.currentTimeMillis()
            val id = requestBuilder.attributes.getOrNull(PeekabooRequestId)
                ?: UUID.randomUUID().toString()

            // Mark so the response pipeline skips double-capturing this call.
            requestBuilder.attributes.put(PeekabooIsMocked, true)

            val requestData = requestBuilder.build()
            // Use FQN to prevent the linter from stripping the @InternalAPI import
            val responseData = io.ktor.client.request.HttpResponseData(
                statusCode = HttpStatusCode.fromValue(mockRule.statusCode),
                requestTime = GMTDate(),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                    mockRule.responseHeaders.forEach { (k, v) -> append(k, v) }
                },
                version = HttpProtocolVersion.HTTP_1_1,
                body = ByteReadChannel(mockRule.responseBody.toByteArray()),
                callContext = currentCoroutineContext()
            )

            NetworkStore.add(
                CapturedRequest(
                    id = id,
                    timestamp = startTime,
                    method = method,
                    url = url,
                    requestHeaders = requestData.headers.entries()
                        .associate { it.key to it.value.joinToString(", ") },
                    requestBody = null,
                    responseCode = mockRule.statusCode,
                    responseHeaders = mockRule.responseHeaders,
                    responseBody = mockRule.responseBody,
                    durationMs = mockRule.delayMs,
                    isMocked = true
                )
            )

            HttpClientCall(client, requestData, responseData)
        } else {
            execute(requestBuilder)
        }
    }

    // Response capture — runs after the engine receives a real response.
    // Intercept at Receive phase, BEFORE Transform (where ContentNegotiation deserializes).
    client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
        val container = subject as? HttpResponseContainer ?: return@intercept
        val (info, body) = container
        if (body !is ByteReadChannel) return@intercept

        if (!PeekabooProvider.isInitialized() || !PeekabooProvider.instance.isRunning()) {
            return@intercept
        }

        val req = context.request

        // Always read & re-emit so ContentNegotiation can deserialize the body.
        val allBytes = try {
            (body as ByteReadChannel).readRemaining().readBytes()
        } catch (_: Exception) {
            ByteArray(0)
        }

        // Mocked calls are already captured in the HttpSend interceptor above.
        if (req.attributes.getOrNull(PeekabooIsMocked) != true) {
            val startTime = req.attributes.getOrNull(PeekabooStartTime)
                ?: System.currentTimeMillis()
            val id = req.attributes.getOrNull(PeekabooRequestId)
                ?: UUID.randomUUID().toString()
            val duration = System.currentTimeMillis() - startTime
            val bodyLimit = 1_000_000

            val bodyText = if (allBytes.isNotEmpty()) {
                String(allBytes.take(bodyLimit).toByteArray(), Charsets.UTF_8)
            } else null

            NetworkStore.add(
                CapturedRequest(
                    id = id,
                    timestamp = startTime,
                    method = req.method.value,
                    url = req.url.toString(),
                    requestHeaders = req.headers.entries()
                        .associate { it.key to it.value.joinToString(", ") },
                    requestBody = null,
                    responseCode = context.response.status.value,
                    responseHeaders = context.response.headers.entries()
                        .associate { it.key to it.value.joinToString(", ") },
                    responseBody = bodyText,
                    durationMs = duration,
                    bodyTruncated = allBytes.size > bodyLimit
                )
            )
        }

        // Re-emit the same bytes so ContentNegotiation (Transform phase) can deserialize them.
        proceedWith(HttpResponseContainer(info, ByteReadChannel(allBytes)))
    }
}

/** Installs [PeekabooKtorPlugin] into this [HttpClientConfig]. */
fun HttpClientConfig<*>.installPeekaboo() {
    install(PeekabooKtorPlugin)
}
