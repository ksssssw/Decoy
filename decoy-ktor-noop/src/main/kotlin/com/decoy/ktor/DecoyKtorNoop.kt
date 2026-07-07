package com.decoy.ktor

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin

/** No-op stub — used in release builds to keep `install(DecoyKtorPlugin)` call sites compiling. */
public val DecoyKtorPlugin: ClientPlugin<Unit> = createClientPlugin("DecoyPlugin") {
    // intentionally empty
}

/** No-op stub — used in release builds to keep [installDecoy] call sites compiling. */
public fun HttpClientConfig<*>.installDecoy() {
    // intentionally empty
}
