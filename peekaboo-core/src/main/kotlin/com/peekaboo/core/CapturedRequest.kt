package com.peekaboo.core

public data class CapturedRequest(
    val id: String,
    val timestamp: Long,
    val method: String,
    val url: String,
    val requestHeaders: Map<String, String>,
    val requestBody: String?,
    val responseCode: Int?,
    val responseHeaders: Map<String, String>,
    val responseBody: String?,
    val durationMs: Long,
    val isMocked: Boolean = false,
    val error: String? = null,
    val bodyTruncated: Boolean = false,
)
