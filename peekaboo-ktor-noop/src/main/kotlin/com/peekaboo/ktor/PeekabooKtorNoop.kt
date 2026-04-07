package com.peekaboo.ktor

import io.ktor.client.HttpClientConfig

/** No-op stub — use in release builds to keep [installPeekaboo] call sites compiling. */
fun HttpClientConfig<*>.installPeekaboo() = Unit
