package com.ksssssw.decoy

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class PostRepository(private val client: HttpClient) {

    private val baseUrl = "https://jsonplaceholder.typicode.com"

    suspend fun getPosts(): List<Post> =
        client.get("$baseUrl/posts").body()

    suspend fun getPost(id: Int): Post =
        client.get("$baseUrl/posts/$id").body()

    suspend fun createPost(post: Post): Post =
        client.post("$baseUrl/posts") {
            contentType(ContentType.Application.Json)
            setBody(post)
        }.body()

    /** Hits a path that answers 404 — for error-screen testing. */
    suspend fun notFound(): Int =
        client.get("$baseUrl/posts/999999999").status.value

    /** Server-side delayed response — for slow-network testing. */
    suspend fun delayed(seconds: Int): Int =
        client.get("https://httpbingo.org/delay/$seconds").status.value
}
