package com.decoy.core

/**
 * Masks credential-bearing header values before they enter [NetworkStore].
 * Captures are served over the unauthenticated loopback API (`/api/calls`, `/ws`),
 * so tokens must never be stored in clear text.
 */
public object HeaderRedactor {
    public const val MASK: String = "[redacted]"

    private val sensitiveNames = setOf(
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "api-key",
        "apikey",
        "x-auth-token",
        "x-access-token",
        "x-amz-security-token",
        "x-goog-api-key",
        "x-csrf-token",
        "x-xsrf-token",
    )

    /** Returns a copy of [headers] with sensitive values replaced by [MASK] (names matched case-insensitively). */
    public fun redact(headers: Map<String, String>): Map<String, String> =
        headers.mapValues { (name, value) ->
            if (isSensitive(name.lowercase())) MASK else value
        }

    // The suffix heuristic catches vendor-specific credential headers
    // (x-client-token, x-webhook-secret, …). Over-redacting a benign header in
    // a debug inspector is a far cheaper mistake than storing a credential.
    private fun isSensitive(lowerName: String): Boolean =
        lowerName in sensitiveNames ||
            lowerName.endsWith("-key") ||
            lowerName.endsWith("-token") ||
            lowerName.endsWith("-secret") ||
            lowerName.endsWith("-auth")
}
