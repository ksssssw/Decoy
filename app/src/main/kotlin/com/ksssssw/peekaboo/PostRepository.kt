package com.ksssssw.peekaboo

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
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
}
