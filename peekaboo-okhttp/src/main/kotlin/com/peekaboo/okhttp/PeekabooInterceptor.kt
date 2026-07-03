package com.peekaboo.okhttp

import com.peekaboo.core.CapturedRequest
import com.peekaboo.core.MockRepository
import com.peekaboo.core.NetworkStore
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID

/**
 * OkHttp interceptor that captures traffic into Peekaboo and applies mock rules.
 *
 * Add it as an application interceptor — works with plain OkHttp and Retrofit:
 * ```
 * OkHttpClient.Builder()
 *     .addInterceptor(PeekabooInterceptor())
 *     .build()
 * ```
 */
public class PeekabooInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val id = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        val requestBody = extractRequestBody(request)

        val mockRule = MockRepository.findMatchingRule(request.url.toString(), request.method)
        if (mockRule != null) {
            if (mockRule.delayMs > 0) {
                try {
                    Thread.sleep(mockRule.delayMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Canceled while applying mock delay")
                }
                if (chain.call().isCanceled()) throw IOException("Canceled")
            }

            val mockResponse = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(mockRule.statusCode)
                .message("Mocked (Peekaboo)")
                .body(mockRule.responseBody.toResponseBody("application/json".toMediaType()))
                .apply { mockRule.responseHeaders.forEach { (k, v) -> header(k, v) } }
                .build()

            NetworkStore.add(CapturedRequest(
                id = id,
                timestamp = startTime,
                method = request.method,
                url = request.url.toString(),
                requestHeaders = request.headers.toMap(),
                requestBody = requestBody,
                responseCode = mockRule.statusCode,
                responseHeaders = mockRule.responseHeaders,
                responseBody = mockRule.responseBody,
                durationMs = mockRule.delayMs,
                isMocked = true
            ))
            return mockResponse
        }

        return try {
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            val (bodyText, truncated) = captureResponseBody(response)

            NetworkStore.add(CapturedRequest(
                id = id,
                timestamp = startTime,
                method = request.method,
                url = request.url.toString(),
                requestHeaders = request.headers.toMap(),
                requestBody = requestBody,
                responseCode = response.code,
                responseHeaders = response.headers.toMap(),
                responseBody = bodyText,
                durationMs = duration,
                bodyTruncated = truncated
            ))

            response
        } catch (e: Exception) {
            NetworkStore.add(CapturedRequest(
                id = id,
                timestamp = startTime,
                method = request.method,
                url = request.url.toString(),
                requestHeaders = request.headers.toMap(),
                requestBody = requestBody,
                responseCode = null,
                responseHeaders = emptyMap(),
                responseBody = null,
                durationMs = System.currentTimeMillis() - startTime,
                error = e.message ?: e.javaClass.simpleName
            ))
            throw e
        }
    }

    /**
     * Captures the response body via [Response.peekBody] so the original stream is
     * left untouched for the caller. Bodies that can't be captured safely
     * (streams, binary, still-compressed) are skipped with a marker string.
     */
    private fun captureResponseBody(response: Response): Pair<String?, Boolean> {
        val body = response.body ?: return null to false
        if (response.code == 204 || response.code == 304) return null to false

        val contentType = body.contentType()
        if (contentType?.type == "text" && contentType.subtype == "event-stream") {
            return "[skipped: event-stream]" to false
        }
        // OkHttp strips Content-Encoding when it transparently decompresses gzip;
        // if the header is still present the bytes are compressed — don't capture.
        val encoding = response.header("Content-Encoding")
        if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
            return "[skipped: content-encoding $encoding]" to false
        }
        if (!isTextLike(contentType)) {
            val size = body.contentLength()
            return "[binary body: ${if (size >= 0) "$size bytes" else "unknown size"}]" to false
        }

        return try {
            val bytes = response.peekBody(BODY_LIMIT + 1).bytes()
            val truncated = bytes.size > BODY_LIMIT
            val text = String(if (truncated) bytes.copyOf(BODY_LIMIT.toInt()) else bytes)
            text to truncated
        } catch (e: IOException) {
            "[unreadable body: ${e.message}]" to false
        }
    }

    private fun isTextLike(contentType: MediaType?): Boolean {
        val type = contentType ?: return true // unknown — attempt capture
        if (type.type == "text") return true
        val subtype = type.subtype.lowercase()
        return subtype.contains("json") || subtype.contains("xml") ||
            subtype.contains("javascript") || subtype == "x-www-form-urlencoded"
    }

    private fun extractRequestBody(request: Request): String? {
        val body = request.body ?: return null
        if (body.isDuplex() || body.isOneShot()) return "[skipped: streaming request body]"
        return runCatching {
            val buffer = Buffer()
            body.writeTo(buffer)
            if (buffer.size > BODY_LIMIT) buffer.readUtf8(BODY_LIMIT) + "…" else buffer.readUtf8()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun Headers.toMap(): Map<String, String> =
        names().associateWith { name -> values(name).joinToString(", ") }

    private companion object {
        const val BODY_LIMIT = 1024 * 1024L // 1MB
    }
}
