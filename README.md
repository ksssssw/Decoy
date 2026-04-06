# Peekaboo — Android Network Inspector

A LeakCanary-style debug network inspector for Android. Add one line and inspect all Ktor/OkHttp traffic in a built-in web UI at `localhost:8090`. Zero production code changes — auto-initializes via ContentProvider, release builds contain zero Peekaboo code.

## Features

- Captures all HTTP requests and responses automatically
- Built-in web server at `localhost:8090` with REST API and WebSocket live updates
- Response mocking with regex URL patterns, delay simulation, and per-rule toggle
- Ktor client plugin included — no separate module needed
- No-op in release — `debugImplementation` only, nothing ships to production
- ContentProvider auto-initialization — no `Application.onCreate()` setup required

---

## Quick Start

**Step 1** — Add a single line to `build.gradle.kts`:

```kotlin
debugImplementation("com.peekaboo:peekaboo-debug:1.0.0")
```

**Step 2** — Install the Ktor plugin in your debug `HttpClient`:

```kotlin
// app/src/debug/.../YourApplication.kt
HttpClient(CIO) {
    installPeekaboo()                   // provided by peekaboo-debug
    install(ContentNegotiation) { ... }
}
```

> Put the `HttpClient` setup in `src/debug/` so it only runs in debug builds.  
> The `src/release/` version omits `installPeekaboo()` — no stubs or guards needed.

**Step 3** — Forward port and open the browser:

```bash
adb forward tcp:8090 tcp:8090
```

```
http://localhost:8090
```

That's it.

---

## OkHttp / Retrofit

```kotlin
// app/src/debug/.../YourApplication.kt
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(PeekabooProvider.instance.getOkHttpInterceptor())
    .build()
```

Same pattern — put this only in `src/debug/`. In `src/release/` omit the interceptor.

---

## Source Set Pattern (mirrors LeakCanary)

```
app/
├── src/
│   ├── debug/
│   │   └── YourApplication.kt   ← installPeekaboo() / addInterceptor(...)
│   └── release/
│       └── YourApplication.kt   ← plain HttpClient, no Peekaboo references
```

This is the same pattern LeakCanary recommends when you need to reference its API from application code. The release APK contains zero Peekaboo code.

---

## Module Structure

| Module | Purpose |
|---|---|
| `peekaboo-debug` | Real implementation — web server + OkHttp interceptor + Ktor client plugin |
| `peekaboo-noop` | No-op stubs (optional, for apps that reference Peekaboo API in main source set) |
| `peekaboo-ktor` | Standalone Ktor client plugin (optional separate artifact) |
| `peekaboo-core` | Shared interfaces & models (transitive — do not declare directly) |

---

## Mock Setup

1. Open `http://localhost:8090` after forwarding the port
2. Go to the **Mocks** tab
3. Click **Add Rule** and configure:
   - **URL Pattern** — regex, e.g. `.*jsonplaceholder.*posts.*`
   - **Method** — `GET`, `POST`, `PUT`, `DELETE`, or `*` for any
   - **Status Code** — e.g. `200`, `404`, `500`
   - **Response Body** — JSON string to return
   - **Delay (ms)** — optional simulated network latency
4. Toggle rules on/off without deleting them

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/calls` | List all captured requests (newest first) |
| GET | `/api/calls/:id` | Get request detail by ID |
| DELETE | `/api/calls` | Clear all captured requests |
| GET | `/api/mocks` | List all mock rules |
| POST | `/api/mocks` | Create a mock rule |
| PUT | `/api/mocks/:id` | Update a mock rule |
| DELETE | `/api/mocks/:id` | Delete a mock rule |
| PATCH | `/api/mocks/:id/toggle` | Toggle mock rule on/off |
| GET | `/api/status` | Inspector status and counts |
| WS | `/ws` | WebSocket — live push of new captured requests |

---

## Architecture

```
Peekaboo/
├── app/                        # Sample app demonstrating single-line integration
│   ├── src/debug/              # Debug Application — installs Peekaboo Ktor plugin
│   └── src/release/            # Release Application — plain HttpClient, no Peekaboo
│
├── peekaboo-debug/             # debugImplementation only
│   ├── PeekabooInterceptor     # OkHttp interceptor
│   ├── installPeekaboo()       # Ktor client plugin (HttpClientConfig extension)
│   ├── NetworkStore            # In-memory ring buffer (max 500 entries)
│   ├── MockRepository          # Thread-safe mock rule storage
│   ├── PeekabooServer          # Ktor embedded HTTP + WebSocket server
│   ├── RealPeekaboo            # Peekaboo interface implementation
│   └── PeekabooInitializer     # ContentProvider — auto-starts server on app launch
│
├── peekaboo-noop/              # Optional — releaseImplementation for apps using Peekaboo API in main source set
├── peekaboo-ktor/              # Optional standalone Ktor client plugin
└── peekaboo-core/              # Shared interfaces (transitive via peekaboo-debug/noop)
```

**Data flow:**

```
HTTP request
    → PeekabooInterceptor / Ktor plugin
        → MockRepository.findMatchingRule()
            ├── match found → return mock response → NetworkStore.add()
            └── no match   → proceed() → NetworkStore.add()
                                              ↓
                                      WebSocket push to browser
                                      REST API polling from browser
```

---

## License

MIT
