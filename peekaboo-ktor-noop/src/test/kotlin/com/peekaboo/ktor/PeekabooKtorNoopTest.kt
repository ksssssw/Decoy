package com.peekaboo.ktor

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.api.ClientPlugin
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Compile-time parity guard: the no-op twin must expose the exact public surface
 * of the real `:peekaboo-ktor` module (`PeekabooKtorPlugin` val + `installPeekaboo()`),
 * so consumer call sites that build in debug also build in release.
 */
class PeekabooKtorNoopTest {

    @Test
    fun `exposes PeekabooKtorPlugin val with the real plugin's name`() {
        val plugin: ClientPlugin<Unit> = PeekabooKtorPlugin
        assertEquals("PeekabooPlugin", plugin.key.name)
    }

    @Test
    fun `installPeekaboo compiles against a plain client config and is a no-op`() {
        val config = HttpClientConfig<HttpClientEngineConfig>()
        config.installPeekaboo() // must not throw, must not register anything visible
    }
}
