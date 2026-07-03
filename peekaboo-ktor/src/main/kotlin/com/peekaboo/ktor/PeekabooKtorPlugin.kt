package com.peekaboo.ktor

import com.peekaboo.core.CapturedRequest
import com.peekaboo.core.MockRepository
import com.peekaboo.core.NetworkStore
import com.peekaboo.core.PeekabooProvider
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.client.statement.HttpResponseContainer
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.delay
import java.util.UUID

private val PeekabooStartTime = AttributeKey<Long>("PeekabooStartTime")
private val PeekabooRequestId = AttributeKey<String>("PeekabooRequestId")
private val PeekabooIsMocked = AttributeKey<Boolean>("PeekabooIsMocked")
private val PeekabooRequestBody = AttributeKey<String>("PeekabooRequestBody")

private const val BODY_LIMIT = 1024 * 1024 // 1MB

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
public val PeekabooKtorPlugin: ClientPlugin<Unit> = createClientPlugin("PeekabooPlugin") {

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

        // ContentNegotiation has already turned the body into OutgoingContent here —
        // capture it once so both the mock path and the response pipeline can log it.
        extractRequestBody(requestBuilder.body)?.let {
            requestBuilder.attributes.put(PeekabooRequestBody, it)
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

            NetworkStore.add(
                CapturedRequest(
                    id = id,
                    timestamp = startTime,
                    method = method,
                    url = url,
                    requestHeaders = requestData.headers.entries()
                        .associate { it.key to it.value.joinToString(", ") },
                    requestBody = requestBuilder.attributes.getOrNull(PeekabooRequestBody),
                    responseCode = mockRule.statusCode,
                    responseHeaders = mockRule.responseHeaders,
                    responseBody = mockRule.responseBody,
                    durationMs = mockRule.delayMs,
                    isMocked = true
                )
            )

            createMockCall(client, requestData, mockRule)
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
            body.readRemaining().readBytes()
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

            val bodyText = if (allBytes.isNotEmpty()) {
                String(allBytes.take(BODY_LIMIT).toByteArray(), Charsets.UTF_8)
            } else null

            NetworkStore.add(
                CapturedRequest(
                    id = id,
                    timestamp = startTime,
                    method = req.method.value,
                    url = req.url.toString(),
                    requestHeaders = req.headers.entries()
                        .associate { it.key to it.value.joinToString(", ") },
                    requestBody = req.attributes.getOrNull(PeekabooRequestBody),
                    responseCode = context.response.status.value,
                    responseHeaders = context.response.headers.entries()
                        .associate { it.key to it.value.joinToString(", ") },
                    responseBody = bodyText,
                    durationMs = duration,
                    bodyTruncated = allBytes.size > BODY_LIMIT
                )
            )
        }

        // Re-emit the same bytes so ContentNegotiation (Transform phase) can deserialize them.
        proceedWith(HttpResponseContainer(info, ByteReadChannel(allBytes)))
    }
}

private fun extractRequestBody(body: Any): String? = when (body) {
    is TextContent -> body.text.take(BODY_LIMIT)
    is OutgoingContent.ByteArrayContent ->
        runCatching { String(body.bytes().take(BODY_LIMIT).toByteArray(), Charsets.UTF_8) }.getOrNull()
    else -> null
}

/** Installs [PeekabooKtorPlugin] into this [HttpClientConfig]. */
public fun HttpClientConfig<*>.installPeekaboo() {
    install(PeekabooKtorPlugin)
}
