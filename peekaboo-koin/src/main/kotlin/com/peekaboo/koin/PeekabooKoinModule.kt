package com.peekaboo.koin

import com.peekaboo.core.PeekabooProvider
import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * Usage:
 * startKoin {
 *     modules(peekabooModule, yourAppModule)
 * }
 *
 * In your app module, define OkHttpClient using [withPeekaboo]:
 * single {
 *     OkHttpClient.Builder().withPeekaboo().build()
 * }
 */
val peekabooModule = module {
    single {
        OkHttpClient.Builder().apply {
            if (PeekabooProvider.isInitialized()) {
                addInterceptor(PeekabooProvider.instance.getOkHttpInterceptor())
            }
        }
    }
}
