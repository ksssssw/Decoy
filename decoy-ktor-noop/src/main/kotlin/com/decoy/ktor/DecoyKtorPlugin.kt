package com.decoy.ktor

// This file MUST be named DecoyKtorPlugin.kt, mirroring the real module: the
// filename determines the JVM facade class (DecoyKtorPluginKt) that Java call
// sites like `DecoyKtorPluginKt.installDecoy(config)` link against — a
// different name compiles in debug and throws NoClassDefFoundError in release.

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
