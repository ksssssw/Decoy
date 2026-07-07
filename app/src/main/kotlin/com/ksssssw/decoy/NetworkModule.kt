package com.ksssssw.decoy

import com.decoy.ktor.installDecoy
import com.decoy.okhttp.DecoyInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.gson.gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.koin.dsl.module

/**
 * Production-style network module.
 *
 * This mirrors what a real service already has: one DI module that owns the
 * OkHttpClient / Retrofit / Ktor HttpClient instances. Integrating Decoy
 * into an existing module like this takes exactly ONE line per HTTP stack
 * (marked with `// ← Decoy` below) — everything else is your usual setup.
 *
 * The same call sites compile in release builds because the `-noop` artifacts
 * ship identical signatures; the swap happens purely in Gradle:
 * `debugImplementation(decoy-okhttp)` / `releaseImplementation(decoy-okhttp-noop)`.
 */
val networkModule = module {

    // ── OkHttp / Retrofit stack ──────────────────────────────
    single {
        OkHttpClient.Builder()
            .addInterceptor(DecoyInterceptor()) // ← Decoy (debug: captures / release: no-op)
            .build()
    }
    single {
        Retrofit.Builder()
            .baseUrl("https://jsonplaceholder.typicode.com/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    single<RetrofitPostApi> { get<Retrofit>().create(RetrofitPostApi::class.java) }

    // ── Ktor stack ───────────────────────────────────────────
    single {
        HttpClient(CIO) {
            installDecoy() // ← Decoy — install BEFORE ContentNegotiation
            install(ContentNegotiation) { gson() }
        }
    }
    single { PostRepository(get()) }
}
