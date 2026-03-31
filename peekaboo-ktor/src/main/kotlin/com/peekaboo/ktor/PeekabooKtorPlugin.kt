package com.peekaboo.ktor

import com.peekaboo.core.CapturedRequest
import com.peekaboo.core.NetworkStore
import com.peekaboo.core.PeekabooProvider
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.readBytes
import java.util.UUID

private val PeekabooStartTime = AttributeKey<Long>("PeekabooStartTime")
private val PeekabooRequestId = AttributeKey<String>("PeekabooRequestId")

/**
 * Ktor client plugin that captures HTTP requests and responses into Peekaboo's NetworkStore.
 *
 * Install this plugin BEFORE ContentNegotiation so it receives the raw response bytes:
 * ```
 * HttpClient(CIO) {
 *     install(PeekabooKtorPlugin)
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

    transformResponseBody { response, content, _ ->
        if (!PeekabooProvider.isInitialized() || !PeekabooProvider.instance.isRunning()) {
            return@transformResponseBody content
        }

        val startTime = response.call.request.attributes.getOrNull(PeekabooStartTime)
            ?: System.currentTimeMillis()
        val id = response.call.request.attributes.getOrNull(PeekabooRequestId)
            ?: UUID.randomUUID().toString()
        val duration = System.currentTimeMillis() - startTime
        val bodyLimit = 1_000_000

        val allBytes = try {
            content.readRemaining().readBytes()
        } catch (_: Exception) {
            ByteArray(0)
        }

        val bodyText = if (allBytes.isNotEmpty()) {
            String(allBytes.take(bodyLimit).toByteArray(), Charsets.UTF_8)
        } else null

        val req = response.call.request
        NetworkStore.add(
            CapturedRequest(
                id = id,
                timestamp = startTime,
                method = req.method.value,
                url = req.url.toString(),
                requestHeaders = req.headers.entries()
                    .associate { it.key to it.value.joinToString(", ") },
                requestBody = null,
                responseCode = response.status.value,
                responseHeaders = response.headers.entries()
                    .associate { it.key to it.value.joinToString(", ") },
                responseBody = bodyText,
                durationMs = duration,
                bodyTruncated = allBytes.size > bodyLimit
            )
        )

        ByteReadChannel(allBytes)
    }
}

/** Installs [PeekabooKtorPlugin] into this [HttpClientConfig]. */
fun HttpClientConfig<*>.installPeekaboo() {
    install(PeekabooKtorPlugin)
}
