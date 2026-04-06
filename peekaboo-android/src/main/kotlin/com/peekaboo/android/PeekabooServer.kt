package com.peekaboo.android

import android.content.Context
import com.google.gson.Gson
import com.peekaboo.core.CapturedRequest
import com.peekaboo.core.MockRepository
import com.peekaboo.core.MockRule
import com.peekaboo.core.NetworkStore
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

internal class PeekabooServer(private val context: Context) {
    private var engine: ApplicationEngine? = null
    private val gson = Gson()
    private val activeSessions = CopyOnWriteArrayList<DefaultWebSocketSession>()

    fun start(port: Int = 8090) {
        engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            install(CORS) { anyHost() }
            install(ContentNegotiation) { gson() }
            install(WebSockets)

            routing {
                // Web UI served from assets/web/
                staticResources("/", "web") {
                    default("index.html")
                }

                // REST API
                route("/api") {
                    get("/calls") {
                        call.respond(NetworkStore.getAll())
                    }

                    get("/calls/{id}") {
                        val found = NetworkStore.getById(call.parameters["id"]!!)
                        if (found != null) call.respond(found)
                        else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                    }

                    delete("/calls") {
                        NetworkStore.clear()
                        call.respond(HttpStatusCode.OK, mapOf("status" to "cleared"))
                    }

                    get("/mocks") {
                        call.respond(MockRepository.getRules())
                    }

                    post("/mocks") {
                        val rule = call.receive<MockRule>()
                        val withId = rule.copy(id = UUID.randomUUID().toString())
                        MockRepository.addRule(withId)
                        call.respond(HttpStatusCode.Created, withId)
                    }

                    put("/mocks/{id}") {
                        val updated = call.receive<MockRule>()
                        MockRepository.updateRule(updated.copy(id = call.parameters["id"]!!))
                        call.respond(HttpStatusCode.OK)
                    }

                    delete("/mocks/{id}") {
                        MockRepository.removeRule(call.parameters["id"]!!)
                        call.respond(HttpStatusCode.OK)
                    }

                    patch("/mocks/{id}/toggle") {
                        MockRepository.toggleRule(call.parameters["id"]!!)
                        call.respond(HttpStatusCode.OK)
                    }

                    get("/status") {
                        call.respond(mapOf(
                            "running" to true,
                            "port" to (engine?.environment?.connectors?.firstOrNull()?.port ?: 8090),
                            "callCount" to NetworkStore.getAll().size,
                            "mockCount" to MockRepository.getRules().size
                        ))
                    }
                }

                // WebSocket - real-time push of new requests
                webSocket("/ws") {
                    activeSessions.add(this)
                    val listener: (CapturedRequest) -> Unit = { capturedCall ->
                        launch {
                            runCatching {
                                send(Frame.Text(gson.toJson(capturedCall)))
                            }
                        }
                    }
                    NetworkStore.addListener(listener)
                    try {
                        for (frame in incoming) { /* keep-alive */ }
                    } catch (e: ClosedSendChannelException) {
                        // normal close
                    } finally {
                        NetworkStore.removeListener(listener)
                        activeSessions.remove(this)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
    }
}
