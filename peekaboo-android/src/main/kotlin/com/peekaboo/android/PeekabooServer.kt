package com.peekaboo.android

import com.google.gson.Gson
import com.peekaboo.core.CapturedRequest
import com.peekaboo.core.MockRepository
import com.peekaboo.core.MockRule
import com.peekaboo.core.NetworkStore
import com.peekaboo.core.RulePlacement
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

// Gson-deserialized request payloads — every field may arrive null (and Gson
// silently turns a missing primitive like isEnabled into false), so the API
// receives DTOs with nullable fields and applies defaults explicitly.
internal data class GroupToggleRequest(val group: String?, val isEnabled: Boolean?)
internal data class GroupRenameRequest(val from: String?, val to: String?)
internal data class ImportRequest(val mode: String?, val rules: List<MockRuleDto?>?)
internal data class LayoutItemDto(val id: String?, val group: String?)
internal data class LayoutRequest(val items: List<LayoutItemDto?>?)

/** Info about the host app, shown in the web UI header. */
internal data class AppInfo(
    val packageName: String,
    val appVersion: String,
    val versionCode: Long,
    val deviceModel: String,
    val sdkInt: Int,
)

internal data class MockRuleDto(
    val id: String?,
    val urlPattern: String?,
    val method: String?,
    val statusCode: Int?,
    val responseBody: String?,
    val responseHeaders: Map<String, String>?,
    val delayMs: Long?,
    val isEnabled: Boolean?,
    val description: String?,
    val createdAt: Long?,
    val group: String?,
) {
    fun toRule(): MockRule = MockRule(
        id = id ?: "",
        urlPattern = urlPattern ?: "",
        method = method ?: "*",
        statusCode = statusCode ?: 200,
        responseBody = responseBody ?: "",
        responseHeaders = responseHeaders ?: emptyMap(),
        delayMs = delayMs ?: 0,
        isEnabled = isEnabled ?: true,
        description = description ?: "",
        createdAt = createdAt?.takeIf { it != 0L } ?: System.currentTimeMillis(),
        group = group ?: "",
    )
}

internal class PeekabooServer(private val appInfo: AppInfo) {
    private var engine: ApplicationEngine? = null

    /**
     * Starts the inspector server bound to loopback only — never reachable from
     * other hosts on the network. Falls back to the next ports if [preferredPort]
     * is taken. Returns the port actually bound.
     */
    fun start(preferredPort: Int = 8090): Int {
        val port = findAvailablePort(preferredPort)
        engine = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            peekabooModule(appInfo) { engine?.environment?.connectors?.firstOrNull()?.port ?: port }
        }.start(wait = false)
        return port
    }

    private fun findAvailablePort(preferred: Int): Int {
        for (candidate in preferred until preferred + 10) {
            try {
                ServerSocket().use { it.bind(InetSocketAddress("127.0.0.1", candidate)) }
                return candidate
            } catch (_: IOException) {
                // port in use — try the next one
            }
        }
        return preferred
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
    }
}

/**
 * The inspector's plugins + routes, extracted from [PeekabooServer] so the API
 * can be exercised with Ktor's `testApplication` without binding a real socket.
 */
internal fun Application.peekabooModule(appInfo: AppInfo, boundPort: () -> Int) {
    val gson = Gson()
    val activeSessions = CopyOnWriteArrayList<DefaultWebSocketSession>()

    install(ContentNegotiation) { gson() }
    install(WebSockets)
    install(StatusPages) {
        // Ktor logs through SLF4J, which is a no-op on Android — surface
        // API errors in Logcat and in the response body instead.
        exception<Throwable> { call, cause ->
            android.util.Log.e("Peekaboo", "Inspector API error: ${call.request.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: cause.toString()))
            )
        }
    }

    routing {
        // Web UI served from classpath resources under web/
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
                val rule = call.receive<MockRuleDto>().toRule()
                val regexError = validatePattern(rule.urlPattern)
                if (regexError != null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to regexError))
                    return@post
                }
                val withId = rule.copy(id = UUID.randomUUID().toString())
                MockRepository.addRule(withId)
                call.respond(HttpStatusCode.Created, withId)
            }

            put("/mocks/{id}") {
                val updated = call.receive<MockRuleDto>().toRule()
                val regexError = validatePattern(updated.urlPattern)
                if (regexError != null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to regexError))
                    return@put
                }
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

            // Constant segments outrank {id} in Ktor routing, so these
            // never collide with /mocks/{id}/... (rule ids are UUIDs anyway).
            patch("/mocks/group/toggle") {
                val body = call.receive<GroupToggleRequest>()
                MockRepository.setGroupEnabled(body.group ?: "", body.isEnabled ?: false)
                call.respond(HttpStatusCode.OK)
            }

            patch("/mocks/all/toggle") {
                val body = call.receive<GroupToggleRequest>()
                MockRepository.setAllEnabled(body.isEnabled ?: false)
                call.respond(HttpStatusCode.OK)
            }

            // Drag & drop result: full ordered layout (order = matching precedence)
            put("/mocks/layout") {
                val body = call.receive<LayoutRequest>()
                val items = body.items.orEmpty().filterNotNull()
                    .mapNotNull { d -> d.id?.let { RulePlacement(it, d.group ?: "") } }
                MockRepository.applyLayout(items)
                call.respond(HttpStatusCode.OK)
            }

            patch("/mocks/group/rename") {
                val body = call.receive<GroupRenameRequest>()
                MockRepository.renameGroup(body.from ?: "", (body.to ?: "").trim())
                call.respond(HttpStatusCode.OK)
            }

            post("/mocks/import") {
                val payload = call.receive<ImportRequest>()
                val incoming = payload.rules.orEmpty().filterNotNull().map { it.toRule() }
                val (valid, invalid) = incoming.partition {
                    it.urlPattern.isNotBlank() && validatePattern(it.urlPattern) == null
                }
                val withIds = valid.map { it.copy(id = UUID.randomUUID().toString()) }
                if (payload.mode == "replace") MockRepository.replaceAll(withIds)
                else MockRepository.addAll(withIds)
                call.respond(mapOf("imported" to withIds.size, "skipped" to invalid.size))
            }

            get("/status") {
                call.respond(mapOf(
                    "running" to true,
                    "port" to boundPort(),
                    "callCount" to NetworkStore.getAll().size,
                    "mockCount" to MockRepository.getRules().size,
                    "packageName" to appInfo.packageName,
                    "appVersion" to appInfo.appVersion,
                    "versionCode" to appInfo.versionCode,
                    "deviceModel" to appInfo.deviceModel,
                    "sdkInt" to appInfo.sdkInt
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
}

private fun validatePattern(pattern: String): String? {
    if (pattern.isBlank()) return "URL pattern must not be empty"
    return runCatching { Regex(pattern) }.exceptionOrNull()
        ?.let { "Invalid regex: ${it.message}" }
}
