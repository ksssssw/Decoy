package com.peekaboo.core

import com.google.gson.annotations.SerializedName

data class MockRule(
    @SerializedName("id") val id: String,
    @SerializedName("urlPattern") val urlPattern: String,
    @SerializedName("method") val method: String,
    @SerializedName("statusCode") val statusCode: Int,
    @SerializedName("responseBody") val responseBody: String,
    @SerializedName("responseHeaders") val responseHeaders: Map<String, String> = emptyMap(),
    @SerializedName("delayMs") val delayMs: Long = 0,
    @SerializedName("isEnabled") val isEnabled: Boolean = true,
    @SerializedName("description") val description: String = ""
)
