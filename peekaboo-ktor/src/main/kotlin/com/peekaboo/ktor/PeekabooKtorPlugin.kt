@file:OptIn(io.ktor.util.InternalAPI::class)

package com.peekaboo.ktor

import com.peekaboo.core.CapturedRequest
import com.peekaboo.core.NetworkStore
import com.peekaboo.core.PeekabooProvider
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.HttpResponseContainer
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import java.util.UUID

private val PeekabooStartTime = AttributeKey<Long>("PeekabooStartTime")
private val PeekabooRequestId = AttributeKey<String>("PeekabooRequestId")

/**
 * Ktor client plugin that captures HTTP requests and responses into Peekaboo's NetworkStore.
 *
 * Install this plugin BEFORE ContentNegotiation:
 * ```
 * HttpClient(CIO) {
 *     installPeekaboo()
 *     install(ContentNegotiation) { gson() }
 * }
 * ```
 *
 * Capture is a no-op when Peekaboo is not running (e.g. release builds with peekaboo-noop).
 */
val PeekabooKtorPlugin = createClientPlugin("PeekabooPlugin") {

    onRequest { request, _ ->
        request.attributes.put(PeekabooStartTime, System.currentTimeMillis())
        request.attributes.put(PeekabooRequestId, UUID.randomUUID().toString())
    }

    // Intercept at Receive phase — BEFORE Transform (where ContentNegotiation deserializes).
    // transformResponseBody cannot be used here because Ktor 2.3.x validates that the
    // returned value matches the requested type (e.g. List<Post>), causing a runtime error
    // when we return ByteReadChannel.
    client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
        val container = subject as? HttpResponseContainer ?: return@intercept
        val (info, body) = container
        if (body !is ByteReadChannel) return@intercept

        if (!PeekabooProvider.isInitialized() || !PeekabooProvider.instance.isRunning()) {
            return@intercept
        }

        // context: HttpClientCall
        val req = context.request
        val startTime = req.attributes.getOrNull(PeekabooStartTime) ?: System.currentTimeMillis()
        val id = req.attributes.getOrNull(PeekabooRequestId) ?: UUID.randomUUID().toString()
        val duration = System.currentTimeMillis() - startTime
        val bodyLimit = 1_000_000

        val allBytes = try {
            (body as ByteReadChannel).readRemaining().readBytes()
        } catch (_: Exception) {
            ByteArray(0)
        }

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

        // Re-emit the same bytes so ContentNegotiation (Transform phase) can deserialize them.
        proceedWith(HttpResponseContainer(info, ByteReadChannel(allBytes)))
    }
}

/** Installs [PeekabooKtorPlugin] into this [HttpClientConfig]. */
fun HttpClientConfig<*>.installPeekaboo() {
    install(PeekabooKtorPlugin)
}
