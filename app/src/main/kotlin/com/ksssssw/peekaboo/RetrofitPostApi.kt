package com.ksssssw.peekaboo

import com.peekaboo.okhttp.PeekabooInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

interface RetrofitPostApi {
    @GET("posts") suspend fun getPosts(): List<Post>
    @GET("posts/{id}") suspend fun getPost(@Path("id") id: Int): Post
    @POST("posts") suspend fun createPost(@Body post: Post): Post
    @GET suspend fun getRaw(@Url url: String): Map<String, Any?>
}

fun createRetrofitApi(): RetrofitPostApi {
    val client = OkHttpClient.Builder()
        .addInterceptor(PeekabooInterceptor()) // debug: captures traffic / release: no-op
        .build()
    return Retrofit.Builder()
        .baseUrl("https://jsonplaceholder.typicode.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RetrofitPostApi::class.java)
}
