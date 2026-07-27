package com.decoy.android

import com.decoy.core.Decoy

internal class RealDecoy(
    private val server: DecoyServer,
) : Decoy {

    // Written on the initializer thread, read from arbitrary network threads
    // via isRunning()/getPort() — @Volatile establishes the happens-before edge.
    @Volatile private var port: Int = 8090
    @Volatile private var running = false

    @Synchronized
    override fun start(port: Int) {
        if (running) {
            android.util.Log.i("Decoy", "Inspector already running at http://localhost:${this.port} — start() ignored")
            return
        }
        this.port = server.start(port)
        running = true
        val who = "${server.appInfo.appName} (${server.appInfo.packageName})"
        android.util.Log.i("Decoy", "Inspector for $who running at http://localhost:${this.port} (loopback only)")
        android.util.Log.i("Decoy", "▶ On your PC: adb forward tcp:${this.port} tcp:${this.port} → open http://localhost:${this.port}")
    }

    @Synchronized
    override fun stop() {
        server.stop()
        running = false
    }

    override fun isRunning(): Boolean = running
    override fun getPort(): Int = port
}
