package com.peekaboo.ktor

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin

/** No-op stub — used in release builds to keep `install(PeekabooKtorPlugin)` call sites compiling. */
public val PeekabooKtorPlugin: ClientPlugin<Unit> = createClientPlugin("PeekabooPlugin") {
    // intentionally empty
}

/** No-op stub — used in release builds to keep [installPeekaboo] call sites compiling. */
public fun HttpClientConfig<*>.installPeekaboo() {
    // intentionally empty
}
