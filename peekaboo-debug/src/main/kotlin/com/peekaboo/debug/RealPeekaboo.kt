package com.peekaboo.debug

import android.content.Context
import com.peekaboo.core.Peekaboo
import okhttp3.Interceptor

class RealPeekaboo(private val context: Context) : Peekaboo {
    private val interceptor = PeekabooInterceptor()
    private var port: Int = 8090
    private val server by lazy { PeekabooServer(context, port) }
    private var running = false

    override fun getOkHttpInterceptor(): Interceptor = interceptor

    override fun start(port: Int) {
        this.port = port
        server.start()
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
