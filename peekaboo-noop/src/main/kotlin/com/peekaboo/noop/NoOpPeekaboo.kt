package com.peekaboo.noop

import com.peekaboo.core.Peekaboo
import okhttp3.Interceptor

class NoOpPeekaboo : Peekaboo {
    override fun getOkHttpInterceptor(): Interceptor =
        Interceptor { chain -> chain.proceed(chain.request()) }
    override fun start(port: Int) {}
    override fun stop() {}
    override fun isRunning(): Boolean = false
    override fun getPort(): Int = -1
}
