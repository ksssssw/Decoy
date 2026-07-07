package com.decoy.ktor

import com.decoy.core.CapturedRequest
import com.decoy.core.HeaderRedactor
import com.decoy.core.MockRepository
import com.decoy.core.NetworkStore
import com.decoy.core.DecoyProvider
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.call.save
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import kotlinx.coroutines.delay
import java.util.UUID

private val DecoyStartTime = AttributeKey<Long>("DecoyStartTime")
private val DecoyRequestId = AttributeKey<String>("DecoyRequestId")

private const val BODY_LIMIT = 1024 * 1024 // 1MB

/**
 * Ktor client plugin that captures HTTP requests/responses into Decoy's NetworkStore
 * and applies mock rules registered via the web UI.
 *
 * Install this plugin BEFORE ContentNegotiation:
 * ```
 * HttpClient(CIO) {
 *     installDecoy()
 *     install(ContentNegotiation) { gson() }
 * }
 * ```
 *
 * All capture happens in the [HttpSend] chain, so every call is recorded — including
 * ones whose body is never consumed (e.g. only `.status` is read), non-2xx responses,
 * and failures (timeouts, connection errors — recorded with [CapturedRequest.error]
 * set, then rethrown). Response bodies are buffered via [HttpClientCall.save] so
 * downstream plugins like ContentNegotiation can still deserialize them; event-stream
 * and binary bodies are skipped with a marker and the original call is returned
 * untouched to keep streaming intact.
 *
 * Note: plugins that re-enter the send chain (e.g. HttpRequestRetry) share the
 * [HttpSend] interceptor list — whether each retry is captured individually depends
 * on install order relative to Decoy.
 */
public val DecoyKtorPlugin: ClientPlugin<Unit> = createClientPlugin("DecoyPlugin") {

    onRequest { request, _ ->
        request.attributes.put(DecoyStartTime, System.currentTimeMillis())
        request.attributes.put(DecoyRequestId, UUID.randomUUID().toString())
    }

    client.plugin(HttpSend).intercept { requestBuilder ->
        if (!DecoyProvider.isInitialized() || !DecoyProvider.instance.isRunning()) {
            return@intercept execute(requestBuilder)
        }

        // ContentNegotiation has already turned the body into OutgoingContent here.
        val requestBody = extractRequestBody(requestBuilder.body)
        val url = requestBuilder.url.buildString()
        val method = requestBuilder.method.value
        val startTime = requestBuilder.attributes.getOrNull(DecoyStartTime)
            ?: System.currentTimeMillis()
        val id = requestBuilder.attributes.getOrNull(DecoyRequestId)
            ?: UUID.randomUUID().toString()

        val mockRule = MockRepository.findMatchingRule(url, method)
        if (mockRule != null) {
            if (mockRule.delayMs > 0) delay(mockRule.delayMs)

            val requestData = requestBuilder.build()

            NetworkStore.add(
                CapturedRequest(
                    id = id,
                    timestamp = startTime,
                    method = method,
                    url = url,
                    requestHeaders = requestData.headers.entries().toRedactedHeaderMap(),
                    requestBody = requestBody,
                    responseCode = mockRule.statusCode,
                    responseHeaders = mockRule.responseHeaders,
                    responseBody = mockRule.responseBody,
                    durationMs = System.currentTimeMillis() - startTime,
                    isMocked = true,
                    mockDelayMs = mockRule.delayMs
                )
            )

            createMockCall(client, requestData, mockRule)
        } else {
            val call = try {
                execute(requestBuilder)
            } catch (t: Throwable) {
                NetworkStore.add(
                    CapturedRequest(
                        id = id,
                        timestamp = startTime,
                        method = method,
                        url = url,
                        requestHeaders = requestBuilder.headers.entries().toRedactedHeaderMap(),
                        requestBody = requestBody,
                        responseCode = null,
                        responseHeaders = emptyMap(),
                        responseBody = null,
                        durationMs = System.currentTimeMillis() - startTime,
                        error = t.message ?: t::class.simpleName ?: "Error"
                    )
                )
                throw t
            }
            captureRealCall(call, id, startTime, requestBody)
        }
    }
}

/**
 * Records a real (non-mocked) call. Returns the call the downstream caller should
 * use: a [save]d replayable call when the body was buffered for capture, or the
 * untouched original when the body must keep streaming (event-stream / binary).
 */
private suspend fun captureRealCall(
    call: HttpClientCall,
    id: String,
    startTime: Long,
    requestBody: String?,
): HttpClientCall {
    val response = call.response
    val contentType = response.contentType()

    fun record(bodyText: String?, truncated: Boolean = false) {
        NetworkStore.add(
            CapturedRequest(
                id = id,
                timestamp = startTime,
                method = call.request.method.value,
                url = call.request.url.toString(),
                requestHeaders = call.request.headers.entries().toRedactedHeaderMap(),
                requestBody = requestBody,
                responseCode = response.status.value,
                responseHeaders = response.headers.entries().toRedactedHeaderMap(),
                responseBody = bodyText,
                durationMs = System.currentTimeMillis() - startTime,
                bodyTruncated = truncated
            )
        )
    }

    if (contentType?.match(ContentType.Text.EventStream) == true) {
        record("[skipped: event-stream]")
        return call
    }
    // Without the ContentEncoding plugin the client hands us still-compressed bytes.
    val encoding = response.headers[HttpHeaders.ContentEncoding]
    if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
        record("[skipped: content-encoding $encoding]")
        return call
    }
    if (!isTextLike(contentType)) {
        val size = response.contentLength()
        record("[binary body: ${size?.let { "$it bytes" } ?: "unknown size"}]")
        return call
    }
    // call.save() buffers the whole response in memory — refuse bodies that
    // declare themselves oversized instead of buffering a multi-MB download
    // just to truncate it. Unknown-length bodies still go through save().
    val declaredLength = response.contentLength()
    if (declaredLength != null && declaredLength > BODY_LIMIT) {
        record("[skipped: body $declaredLength bytes]")
        return call
    }

    val saved = try {
        call.save()
    } catch (e: Exception) {
        record("[unreadable body: ${e.message}]")
        return call
    }
    val bytes: ByteArray = saved.response.body()
    val truncated = bytes.size > BODY_LIMIT
    val text = when {
        bytes.isEmpty() -> null
        // A cut at the byte limit can land mid-UTF-8-sequence — drop the
        // resulting replacement char instead of showing a garbled tail.
        truncated -> String(bytes.copyOf(BODY_LIMIT), Charsets.UTF_8).trimEnd('�')
        else -> String(bytes, Charsets.UTF_8)
    }
    record(text, truncated)
    return saved
}

// Credential headers are masked before entering the store — captures are
// served over the unauthenticated loopback API and must never carry tokens.
private fun Set<Map.Entry<String, List<String>>>.toRedactedHeaderMap(): Map<String, String> =
    HeaderRedactor.redact(associate { it.key to it.value.joinToString(", ") })

private fun isTextLike(contentType: ContentType?): Boolean {
    val type = contentType ?: return true // unknown — attempt capture
    if (type.contentType == "text") return true
    val subtype = type.contentSubtype.lowercase()
    return subtype.contains("json") || subtype.contains("xml") ||
        subtype.contains("javascript") || subtype == "x-www-form-urlencoded"
}

private fun extractRequestBody(body: Any): String? = when (body) {
    is TextContent -> body.text.take(BODY_LIMIT)
    is OutgoingContent.ByteArrayContent ->
        runCatching { String(body.bytes().take(BODY_LIMIT).toByteArray(), Charsets.UTF_8) }.getOrNull()
    else -> null
}

/** Installs [DecoyKtorPlugin] into this [HttpClientConfig]. */
public fun HttpClientConfig<*>.installDecoy() {
    install(DecoyKtorPlugin)
}
