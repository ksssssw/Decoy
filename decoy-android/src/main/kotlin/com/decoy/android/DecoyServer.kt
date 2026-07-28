package com.decoy.android

import com.google.gson.Gson
import com.decoy.core.CapturedRequest
import com.decoy.core.MockRepository
import com.decoy.core.MockRule
import com.decoy.core.NetworkStore
import com.decoy.core.RulePlacement
import io.ktor.http.*
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

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
    val appName: String,
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
        responseHeaders = responseHeaders.sanitizedHeaders(),
        delayMs = delayMs ?: 0,
        isEnabled = isEnabled ?: true,
        description = description ?: "",
        createdAt = createdAt?.takeIf { it != 0L } ?: System.currentTimeMillis(),
        group = group ?: "",
    )
}

internal class DecoyServer(internal val appInfo: AppInfo) {
    private var engine: EmbeddedServer<*, *>? = null
    private var currentPort: Int = -1

    /**
     * Starts the inspector server bound to loopback only — never reachable from
     * other hosts on the network. Falls back to the next ports if [preferredPort]
     * is taken. Returns the port actually bound.
     *
     * Idempotent: a second start() returns the already-bound port instead of
     * orphaning the running engine (which would leak its listener socket and
     * silently move the UI off an already-issued `adb forward`). Out-of-range
     * ports fall back to the default — reachable via the public Decoy.start(),
     * this must degrade gracefully, never throw into the host app.
     */
    @Synchronized
    fun start(preferredPort: Int = DEFAULT_PORT): Int {
        engine?.let { return currentPort }
        val base = if (preferredPort in 1024..65526) {
            preferredPort
        } else {
            android.util.Log.w("Decoy", "Invalid port $preferredPort — using $DEFAULT_PORT")
            DEFAULT_PORT
        }
        val boundPort = findAvailablePort(base)
        // Ktor 3.x dropped ApplicationEnvironment.connectors; the actually-bound port
        // is only available via engine.resolvedConnectors() (suspend). We already
        // pre-bound and confirmed [boundPort] in findAvailablePort and tell CIO to bind
        // exactly it, so [boundPort] is the source of truth — no connector lookup needed.
        engine = embeddedServer(
            CIO,
            configure = {
                // Rebind the preferred port over TIME_WAIT leftovers after an app
                // restart instead of silently drifting to the next port (which breaks
                // an already-issued `adb forward`). SO_REUSEADDR does NOT let two live
                // listeners share a port, so the multi-app fallback scan still works.
                // Must match the probe socket's reuseAddress in findAvailablePort —
                // if only the probe had it, the probe could pass and this bind fail.
                reuseAddress = true
                connector {
                    host = "127.0.0.1"
                    port = boundPort
                }
            },
        ) {
            decoyModule(appInfo) { boundPort }
        }.start(wait = false)
        currentPort = boundPort
        return boundPort
    }

    private fun findAvailablePort(preferred: Int): Int {
        for (candidate in preferred until preferred + 10) {
            try {
                ServerSocket().use {
                    it.reuseAddress = true
                    it.bind(InetSocketAddress("127.0.0.1", candidate))
                }
                return candidate
            } catch (_: Exception) {
                // in use or unbindable (IOException, SecurityException, …) — try the next one
            }
        }
        // Failing loudly beats silently returning a taken port: the initializer
        // logs this exception, making "web UI unreachable" diagnosable in Logcat.
        throw IOException("Decoy: no free port in $preferred..${preferred + 9}")
    }

    @Synchronized
    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
        currentPort = -1
    }

    private companion object {
        const val DEFAULT_PORT = 8090
    }
}

/**
 * The inspector's plugins + routes, extracted from [DecoyServer] so the API
 * can be exercised with Ktor's `testApplication` without binding a real socket.
 */
internal fun Application.decoyModule(appInfo: AppInfo, boundPort: () -> Int) {
    val gson = Gson()

    install(ContentNegotiation) { gson() }
    // Ktor buffers request bodies with no default cap — without this an import
    // of a few hundred MB would be parsed straight into the host app's heap.
    install(RequestBodyLimit) { bodyLimit { MAX_REQUEST_BODY_BYTES } }
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
        // The UI only sends keep-alives; anything bigger than this is not ours.
        maxFrameSize = 1L * 1024 * 1024
    }
    install(StatusPages) {
        // Ktor logs through SLF4J, which is a no-op on Android — surface
        // API errors in Logcat and in the response body instead. Client
        // errors get proper 4xx codes; internal messages are never echoed.
        exception<BadRequestException> { call, cause ->
            android.util.Log.w("Decoy", "Bad request: ${call.request.uri}", cause)
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Malformed request"))
        }
        exception<JsonConvertException> { call, cause ->
            android.util.Log.w("Decoy", "Malformed JSON: ${call.request.uri}", cause)
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Malformed JSON body"))
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                mapOf("error" to "Content-Type must be application/json")
            )
        }
        // RequestBodyLimit signals via this exception; without a handler the
        // catch-all below would turn it into a 500.
        exception<PayloadTooLargeException> { call, _ ->
            call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "Request body too large"))
        }
        exception<Throwable> { call, cause ->
            android.util.Log.e("Decoy", "Inspector API error: ${call.request.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal error"))
        }
    }

    // Loopback binding alone doesn't stop DNS rebinding: a page on an attacker's
    // domain whose DNS re-resolves to 127.0.0.1 becomes same-origin with the
    // inspector, so its fetches succeed and are readable. A rebound request
    // still carries the attacker's hostname in Host (and the page's Origin),
    // so reject both before any route runs. WebSocket upgrades keep their own
    // in-handler Origin check, which closes with a policy code the browser
    // console surfaces.
    intercept(ApplicationCallPipeline.Plugins) {
        val host = call.request.headers[HttpHeaders.Host]
        if (host != null && !TRUSTED_HOST.matches(host)) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Untrusted Host header"))
            return@intercept finish()
        }
        val isWebSocketUpgrade =
            call.request.headers[HttpHeaders.Upgrade]?.equals("websocket", ignoreCase = true) == true
        val origin = call.request.headers[HttpHeaders.Origin]
        if (!isWebSocketUpgrade && origin != null && !TRUSTED_ORIGIN.matches(origin)) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Cross-origin request rejected"))
            return@intercept finish()
        }
    }

    routing {
        // Web UI served from classpath resources under decoy-web/ — namespaced so
        // no transitive dependency's resources can collide into the served root.
        staticResources("/", "decoy-web") {
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
                val dto = call.receive<MockRuleDto>()
                val error = dto.validationError()
                if (error != null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
                    return@post
                }
                val withId = dto.toRule().copy(id = UUID.randomUUID().toString())
                MockRepository.addRule(withId)
                call.respond(HttpStatusCode.Created, withId)
            }

            put("/mocks/{id}") {
                val dto = call.receive<MockRuleDto>()
                val error = dto.validationError()
                if (error != null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
                    return@put
                }
                MockRepository.updateRule(dto.toRule().copy(id = call.parameters["id"]!!))
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
                // Imported files are the sharing path — invalid headers are dropped
                // silently by toRule(), but a rule with a bad pattern or out-of-range
                // numbers is skipped and reported, never stored.
                val incoming = payload.rules.orEmpty().filterNotNull().map { it.toRule() }
                val (valid, invalid) = incoming.partition { validateRule(it) == null }
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
                    "appName" to appInfo.appName,
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
            // Browsers don't apply CORS to WebSockets — without this check any page
            // open in the device browser could read the full capture stream. Allow
            // only same-device pages (localhost/127.0.0.1 on any port) and
            // non-browser clients, which send no Origin header.
            val origin = call.request.headers[HttpHeaders.Origin]
            if (origin != null && !TRUSTED_ORIGIN.matches(origin)) {
                android.util.Log.w("Decoy", "Rejected cross-origin WebSocket from $origin")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Cross-origin WebSocket rejected"))
                return@webSocket
            }
            // Bounded outbox drained by a single writer: a slow or dead peer drops
            // the oldest frames instead of queueing coroutines and JSON strings
            // without limit (the UI treats the REST API as the source of truth and
            // refetches on reconnect, so lost frames are tolerable). Serialization
            // also moves off the capture thread onto the writer.
            val outbox = Channel<CapturedRequest>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            val listener: (CapturedRequest) -> Unit = { capturedCall ->
                outbox.trySend(capturedCall)
            }
            val writer = launch {
                for (capturedCall in outbox) {
                    send(Frame.Text(gson.toJson(capturedCall)))
                }
            }
            NetworkStore.addListener(listener)
            try {
                for (frame in incoming) { /* keep-alive */ }
            } catch (e: ClosedSendChannelException) {
                // normal close
            } finally {
                NetworkStore.removeListener(listener)
                outbox.close()
                writer.cancel()
            }
        }
    }
}

private val TRUSTED_ORIGIN = Regex("^https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?$")
private val TRUSTED_HOST = Regex("^(localhost|127\\.0\\.0\\.1|\\[::1])(:\\d+)?$", RegexOption.IGNORE_CASE)

// Requests are matched against rule patterns synchronously on the app's network
// threads, so an import of a hundred-KB pattern is rejected outright; the match
// itself is additionally deadline-guarded in MockRepository.
private const val MAX_PATTERN_LENGTH = 1000
private const val MAX_REQUEST_BODY_BYTES = 10L * 1024 * 1024

private fun validatePattern(pattern: String): String? {
    if (pattern.isBlank()) return "URL pattern must not be empty"
    if (pattern.length > MAX_PATTERN_LENGTH) return "URL pattern too long (max $MAX_PATTERN_LENGTH chars)"
    return runCatching { Regex(pattern) }.exceptionOrNull()
        ?.let { "Invalid regex: ${it.message}" }
}

private fun validateRule(rule: MockRule): String? {
    validatePattern(rule.urlPattern)?.let { return it }
    if (rule.statusCode !in VALID_STATUS_RANGE) return "statusCode must be in $VALID_STATUS_RANGE"
    if (rule.delayMs !in VALID_DELAY_RANGE) return "delayMs must be in $VALID_DELAY_RANGE"
    return null
}

/**
 * Full validation for POST/PUT, where the caller deserves a 400 with the field
 * that's wrong. Header entries are checked on the raw map — after [MockRuleDto.toRule]
 * invalid ones are already silently dropped (which is the right behavior for the
 * import and persisted-file paths, but would hide typos from the UI).
 */
private fun MockRuleDto.validationError(): String? {
    responseHeaders?.forEach { (name, value) ->
        @Suppress("SENSELESS_COMPARISON")
        if (name == null || value == null) return "Response headers must not contain nulls"
        if (!isValidHeaderName(name)) return "Invalid response header name: \"$name\""
        if (!isValidHeaderValue(value)) return "Invalid value for response header \"$name\""
    }
    return validateRule(toRule())
}
