package com.decoy.core

public data class MockRule(
    val id: String,
    val urlPattern: String,
    val method: String,
    val statusCode: Int,
    val responseBody: String,
    val responseHeaders: Map<String, String> = emptyMap(),
    val delayMs: Long = 0,
    val isEnabled: Boolean = true,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Free-form group name (per screen, per test case, …). Empty = ungrouped. */
    val group: String = "",
)

/** One entry of the rule layout: which rule sits where, in which group. */
public data class RulePlacement(val id: String, val group: String)
