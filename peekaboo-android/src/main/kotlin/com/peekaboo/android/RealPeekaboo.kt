package com.peekaboo.android

import com.peekaboo.core.Peekaboo

internal class RealPeekaboo(
    private val server: PeekabooServer,
) : Peekaboo {

    private var port: Int = 8090
    private var running = false

    override fun start(port: Int) {
        this.port = server.start(port)
        running = true
        android.util.Log.i("Peekaboo", "Inspector running at http://localhost:${this.port} (loopback only)")
        android.util.Log.i("Peekaboo", "▶ On your PC: adb forward tcp:${this.port} tcp:${this.port} → open http://localhost:${this.port}")
    }

    override fun stop() {
        server.stop()
        running = false
    }

    override fun isRunning(): Boolean = running
    override fun getPort(): Int = port
}
