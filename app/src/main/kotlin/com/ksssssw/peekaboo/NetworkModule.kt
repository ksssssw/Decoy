package com.ksssssw.peekaboo

import com.peekaboo.ktor.installPeekaboo
import com.peekaboo.okhttp.PeekabooInterceptor
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
 * OkHttpClient / Retrofit / Ktor HttpClient instances. Integrating Peekaboo
 * into an existing module like this takes exactly ONE line per HTTP stack
 * (marked with `// ← Peekaboo` below) — everything else is your usual setup.
 *
 * The same call sites compile in release builds because the `-noop` artifacts
 * ship identical signatures; the swap happens purely in Gradle:
 * `debugImplementation(peekaboo-okhttp)` / `releaseImplementation(peekaboo-okhttp-noop)`.
 */
val networkModule = module {

    // ── OkHttp / Retrofit stack ──────────────────────────────
    single {
        OkHttpClient.Builder()
            .addInterceptor(PeekabooInterceptor()) // ← Peekaboo (debug: captures / release: no-op)
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
            installPeekaboo() // ← Peekaboo — install BEFORE ContentNegotiation
            install(ContentNegotiation) { gson() }
        }
    }
    single { PostRepository(get()) }
}
