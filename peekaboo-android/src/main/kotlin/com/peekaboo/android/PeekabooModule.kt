package com.peekaboo.android

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal val peekabooModule = module {
    single { PeekabooInterceptor() }
    single { PeekabooServer(androidContext()) }
    single { RealPeekaboo(get<PeekabooInterceptor>(), get<PeekabooServer>()) }
}

/** Creates an isolated Koin context for Peekaboo — does not touch the host app's Koin instance. */
internal fun createPeekabooKoin(context: Context): KoinApplication =
    koinApplication {
        androidContext(context)
        modules(peekabooModule)
    }
