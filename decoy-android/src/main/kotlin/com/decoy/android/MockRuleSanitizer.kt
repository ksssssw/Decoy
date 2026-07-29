package com.decoy.android

import com.decoy.core.MockRule

/**
 * Gson instantiates [MockRule] reflectively, bypassing the constructor — so
 * non-null fields can silently hold null when the JSON omits them (API clients,
 * old persisted files). Calling `copy()` on such an instance throws NPE.
 * This rebuilds the rule through the real constructor with safe fallbacks.
 * Out-of-range numbers are clamped rather than dropped — losing a persisted
 * rule over a bad value is worse than normalizing it.
 */
@Suppress("USELESS_ELVIS")
internal fun MockRule.sanitized(): MockRule = MockRule(
    id = id ?: "",
    urlPattern = urlPattern ?: "",
    method = method ?: "*",
    statusCode = if (statusCode in VALID_STATUS_RANGE) statusCode else 200,
    responseBody = responseBody ?: "",
    responseHeaders = responseHeaders.sanitizedHeaders(),
    delayMs = delayMs.coerceIn(VALID_DELAY_RANGE.first, VALID_DELAY_RANGE.last),
    isEnabled = isEnabled,
    description = description ?: "",
    createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
    group = group ?: "",
)

internal val VALID_STATUS_RANGE: IntRange = 100..599
internal val VALID_DELAY_RANGE: LongRange = 0L..60_000L

/**
 * Drops header entries that would make the response builders throw when the
 * mock is served: Gson puts nulls into `Map<String, String>` values (type
 * erasure), and OkHttp/Ktor reject non-token names and non-ASCII values with
 * unchecked exceptions — which would kill the host app's in-flight request.
 */
internal fun Map<String, String>?.sanitizedHeaders(): Map<String, String> =
    orEmpty().filter { (name, value) ->
        @Suppress("SENSELESS_COMPARISON")
        name != null && value != null && isValidHeaderName(name) && isValidHeaderValue(value)
    }

/** RFC 7230 token — the charset both OkHttp's and Ktor's header builders accept. */
internal fun isValidHeaderName(name: String): Boolean =
    name.isNotEmpty() && name.all { (it.isLetterOrDigit() && it.code < 128) || it in "!#$%&'*+-.^_`|~" }

/** Mirrors OkHttp's checkValue (the strictest consumer): tab or printable ASCII. */
internal fun isValidHeaderValue(value: String): Boolean =
    value.all { it == '\t' || it in ' '..'~' }
