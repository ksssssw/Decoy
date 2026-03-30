package com.peekaboo.core

interface Peekaboo {
    fun getOkHttpInterceptor(): okhttp3.Interceptor
    fun start(port: Int = 8090)
    fun stop()
    fun isRunning(): Boolean
    fun getPort(): Int
}
