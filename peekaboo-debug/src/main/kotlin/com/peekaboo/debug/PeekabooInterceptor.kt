package com.peekaboo.debug

import com.peekaboo.core.CapturedRequest
import com.peekaboo.core.NetworkStore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.util.UUID

class PeekabooInterceptor : Interceptor {

    private val bodyLimit = 1024 * 1024L  // 1MB

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val id = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        val requestBody = extractRequestBody(request)

        // Check for mock rule
        val mockRule = MockRepository.findMatchingRule(request)
        if (mockRule != null) {
            if (mockRule.delayMs > 0) Thread.sleep(mockRule.delayMs)

            val mockResponse = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(mockRule.statusCode)
                .message("Mocked")
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

        // Real request
        return try {
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime

            val responseBodyBytes = response.body?.bytes()
            val isTruncated = (responseBodyBytes?.size ?: 0) > bodyLimit
            val responseBodyStr = responseBodyBytes
                ?.take(bodyLimit.toInt())
                ?.let { String(it.toByteArray()) }

            val newBody = responseBodyBytes?.toResponseBody(response.body?.contentType())
                ?: "".toResponseBody()

            NetworkStore.add(CapturedRequest(
                id = id,
                timestamp = startTime,
                method = request.method,
                url = request.url.toString(),
                requestHeaders = request.headers.toMap(),
                requestBody = requestBody,
                responseCode = response.code,
                responseHeaders = response.headers.toMap(),
                responseBody = responseBodyStr,
                durationMs = duration,
                bodyTruncated = isTruncated
            ))

            response.newBuilder().body(newBody).build()
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
                error = e.message
            ))
            throw e
        }
    }

    private fun extractRequestBody(request: Request): String? {
        return runCatching {
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            buffer.readUtf8().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun Headers.toMap(): Map<String, String> =
        names().associateWith { name -> values(name).joinToString(", ") }
}
