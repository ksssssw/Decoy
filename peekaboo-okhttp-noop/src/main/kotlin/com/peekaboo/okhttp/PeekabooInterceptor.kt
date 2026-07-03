package com.peekaboo.okhttp

import okhttp3.Interceptor
import okhttp3.Response

/** No-op stub — used in release builds to keep [PeekabooInterceptor] call sites compiling. */
public class PeekabooInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
