package com.decoy.android

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Binds real CIO sockets on loopback (fine in JVM tests) to guard the server
 * lifecycle: idempotent start, graceful handling of nonsense ports, and
 * stop→start rebinding — none of which [DecoyServerRoutesTest] (socketless
 * `testApplication`) can observe.
 *
 * Uses a non-default preferred port so a Decoy instance running on the dev
 * machine can't collide with the tests.
 */
class DecoyServerLifecycleTest {

    private fun server() = DecoyServer(
        AppInfo(
            appName = "Test App",
            packageName = "com.test.app",
            appVersion = "1.0",
            versionCode = 1L,
            deviceModel = "JVM",
            sdkInt = 34,
        )
    )

    @Test
    fun `start is idempotent and reports the same port`() {
        val s = server()
        try {
            val first = s.start(18090)
            val second = s.start(18090)
            assertEquals(first, second, "second start() must not rebind or drift ports")
        } finally {
            s.stop()
        }
    }

    @Test
    fun `out-of-range port falls back to the default range instead of throwing`() {
        val s = server()
        try {
            val port = s.start(70000)
            assertTrue(port in 8090..8099, "expected fallback into 8090..8099, got $port")
        } finally {
            s.stop()
        }
    }

    @Test
    fun `port zero falls back to the default range instead of binding ephemeral`() {
        val s = server()
        try {
            val port = s.start(0)
            assertTrue(port in 8090..8099, "expected fallback into 8090..8099, got $port")
        } finally {
            s.stop()
        }
    }

    @Test
    fun `stop then start rebinds the same preferred port`() {
        val s = server()
        val first = s.start(18090)
        s.stop()
        try {
            val second = s.start(18090)
            assertEquals(first, second, "SO_REUSEADDR must let the port rebind after stop()")
        } finally {
            s.stop()
        }
    }

    @Test
    fun `two servers never share a port`() {
        val a = server()
        val b = server()
        try {
            val portA = a.start(18090)
            val portB = b.start(18090)
            assertTrue(portA != portB, "both instances claimed port $portA")
        } finally {
            a.stop()
            b.stop()
        }
    }
}
