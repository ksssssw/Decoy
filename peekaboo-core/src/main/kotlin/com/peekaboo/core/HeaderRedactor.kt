package com.peekaboo.core

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
    )

    /** Returns a copy of [headers] with sensitive values replaced by [MASK] (names matched case-insensitively). */
    public fun redact(headers: Map<String, String>): Map<String, String> =
        headers.mapValues { (name, value) ->
            if (name.lowercase() in sensitiveNames) MASK else value
        }
}
