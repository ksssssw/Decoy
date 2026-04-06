package com.peekaboo.android

import com.peekaboo.core.Peekaboo
import okhttp3.Interceptor

internal class RealPeekaboo(
    private val interceptor: PeekabooInterceptor,
    private val server: PeekabooServer,
) : Peekaboo {

    private var port: Int = 8090
    private var running = false

    override fun getOkHttpInterceptor(): Interceptor = interceptor

    override fun start(port: Int) {
        this.port = port
        server.start(port)
        running = true
        android.util.Log.d("Peekaboo", "Peekaboo running at http://localhost:$port")
        android.util.Log.d("Peekaboo", "▶ Run on Mac: adb forward tcp:$port tcp:$port")
    }

    override fun stop() {
        server.stop()
        running = false
    }

    override fun isRunning(): Boolean = running
    override fun getPort(): Int = port
}
