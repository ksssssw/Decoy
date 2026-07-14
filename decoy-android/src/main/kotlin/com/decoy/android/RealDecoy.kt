package com.decoy.android

import com.decoy.core.Decoy

internal class RealDecoy(
    private val server: DecoyServer,
) : Decoy {

    private var port: Int = 8090
    private var running = false

    override fun start(port: Int) {
        this.port = server.start(port)
        running = true
        val who = "${server.appInfo.appName} (${server.appInfo.packageName})"
        android.util.Log.i("Decoy", "Inspector for $who running at http://localhost:${this.port} (loopback only)")
        android.util.Log.i("Decoy", "▶ On your PC: adb forward tcp:${this.port} tcp:${this.port} → open http://localhost:${this.port}")
    }

    override fun stop() {
        server.stop()
        running = false
    }

    override fun isRunning(): Boolean = running
    override fun getPort(): Int = port
}
