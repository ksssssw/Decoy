package com.decoy.ktor

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.api.ClientPlugin
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compile-time parity guard: the no-op twin must expose the exact public surface
 * of the real `:decoy-ktor` module (`DecoyKtorPlugin` val + `installDecoy()`),
 * so consumer call sites that build in debug also build in release.
 */
class DecoyKtorNoopTest {

    @Test
    fun `exposes DecoyKtorPlugin val with the real plugin's name`() {
        val plugin: ClientPlugin<Unit> = DecoyKtorPlugin
        assertEquals("DecoyPlugin", plugin.key.name)
    }

    @Test
    fun `installDecoy compiles against a plain client config and is a no-op`() {
        val config = HttpClientConfig<HttpClientEngineConfig>()
        config.installDecoy() // must not throw, must not register anything visible
    }

    @Test
    fun `JVM facade class matches the real module for Java callers`() {
        // Java call sites link against the file facade (DecoyKtorPluginKt), which is
        // derived from the FILE NAME — the noop source file must stay DecoyKtorPlugin.kt
        // or Java consumers compile in debug and crash in release.
        val facade = Class.forName("com.decoy.ktor.DecoyKtorPluginKt")
        assertTrue(facade.methods.any { it.name == "installDecoy" })
        assertTrue(facade.methods.any { it.name == "getDecoyKtorPlugin" })
    }
}
