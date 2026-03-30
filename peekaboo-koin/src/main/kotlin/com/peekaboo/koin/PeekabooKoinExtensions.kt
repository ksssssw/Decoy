package com.peekaboo.koin

import com.peekaboo.core.PeekabooProvider
import okhttp3.OkHttpClient

/**
 * Extension to add the Peekaboo interceptor to an existing OkHttpClient.Builder.
 *
 * Usage:
 * single { OkHttpClient.Builder().withPeekaboo().build() }
 */
fun OkHttpClient.Builder.withPeekaboo(): OkHttpClient.Builder {
    if (PeekabooProvider.isInitialized()) {
        addInterceptor(PeekabooProvider.instance.getOkHttpInterceptor())
    }
    return this
}
