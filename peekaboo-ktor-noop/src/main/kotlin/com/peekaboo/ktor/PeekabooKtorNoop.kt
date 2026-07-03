package com.peekaboo.ktor

import io.ktor.client.HttpClientConfig

/** No-op stub — used in release builds to keep [installPeekaboo] call sites compiling. */
public fun HttpClientConfig<*>.installPeekaboo() {
    // intentionally empty
}
