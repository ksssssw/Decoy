package com.ksssssw.peekaboo

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
