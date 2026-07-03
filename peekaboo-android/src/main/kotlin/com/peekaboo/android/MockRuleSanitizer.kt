package com.peekaboo.android

import com.peekaboo.core.MockRule

/**
 * Gson instantiates [MockRule] reflectively, bypassing the constructor — so
 * non-null fields can silently hold null when the JSON omits them (API clients,
 * old persisted files). Calling `copy()` on such an instance throws NPE.
 * This rebuilds the rule through the real constructor with safe fallbacks.
 */
@Suppress("USELESS_ELVIS")
internal fun MockRule.sanitized(): MockRule = MockRule(
    id = id ?: "",
    urlPattern = urlPattern ?: "",
    method = method ?: "*",
    statusCode = statusCode,
    responseBody = responseBody ?: "",
    responseHeaders = responseHeaders ?: emptyMap(),
    delayMs = delayMs,
    isEnabled = isEnabled,
    description = description ?: "",
    createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
    group = group ?: "",
)
