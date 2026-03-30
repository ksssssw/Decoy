package com.peekaboo.core

import com.google.gson.annotations.SerializedName

data class CapturedRequest(
    @SerializedName("id") val id: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("method") val method: String,
    @SerializedName("url") val url: String,
    @SerializedName("requestHeaders") val requestHeaders: Map<String, String>,
    @SerializedName("requestBody") val requestBody: String?,
    @SerializedName("responseCode") val responseCode: Int?,
    @SerializedName("responseHeaders") val responseHeaders: Map<String, String>,
    @SerializedName("responseBody") val responseBody: String?,
    @SerializedName("durationMs") val durationMs: Long,
    @SerializedName("isMocked") val isMocked: Boolean = false,
    @SerializedName("error") val error: String? = null,
    @SerializedName("bodyTruncated") val bodyTruncated: Boolean = false
)
