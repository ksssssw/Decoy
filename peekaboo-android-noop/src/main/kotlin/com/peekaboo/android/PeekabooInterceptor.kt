package com.peekaboo.android

import okhttp3.Interceptor
import okhttp3.Response

/** No-op stub — use in release builds to keep [PeekabooInterceptor] references compiling. */
class PeekabooInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
