package com.decoy.okhttp

import okhttp3.Interceptor
import okhttp3.Response

/** No-op stub — used in release builds to keep [DecoyInterceptor] call sites compiling. */
public class DecoyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
