package com.ksssssw.peekaboo

import android.app.Application
import com.peekaboo.ktor.installPeekaboo
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SampleApplication)
            modules(appModule)
        }
    }
}

val appModule = module {
    single {
        HttpClient(CIO) {
            installPeekaboo()  // debug: captures traffic / release: no-op
            install(ContentNegotiation) { gson() }
        }
    }
    single { PostRepository(get()) }
}
