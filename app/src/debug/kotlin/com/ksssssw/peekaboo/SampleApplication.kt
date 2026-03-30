package com.ksssssw.peekaboo

import android.app.Application
import com.peekaboo.koin.peekabooModule
import com.peekaboo.koin.withPeekaboo
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@SampleApplication)
            modules(peekabooModule, appModule)
        }
    }
}

val appModule = module {
    single {
        OkHttpClient.Builder()
            .withPeekaboo()
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl("https://jsonplaceholder.typicode.com/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    single { get<Retrofit>().create(SampleApiService::class.java) }
}
