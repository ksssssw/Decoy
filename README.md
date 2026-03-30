# Peekaboo — Android Network Inspector

A LeakCanary-style debug network inspector for Android. Add one line and inspect all OkHttp/Retrofit traffic in a built-in web UI at `localhost:8090`. Zero code changes required — auto-initializes via ContentProvider.

## Features

- Captures all OkHttp/Retrofit requests and responses automatically
- Built-in web server at `localhost:8090` with REST API and WebSocket live updates
- Response mocking with regex URL patterns, delay simulation, and per-rule toggle
- Koin integration module for automatic interceptor injection
- No-op release variant — inspector code is excluded from release builds
- ContentProvider auto-initialization — no `Application.onCreate()` setup needed

## Screenshots

> _(Add screenshots to `screenshots/` folder)_

## Quick Start

**Step 1** — Add to `build.gradle.kts`:
```kotlin
debugImplementation("com.peekaboo:inspector-debug:1.0.0")
releaseImplementation("com.peekaboo:inspector-noop:1.0.0")
```

**Step 2** — Forward port from device to host:
```bash
adb forward tcp:8090 tcp:8090
```

**Step 3** — Open browser:
```
http://localhost:8090
```

That's it. No other code changes needed.

---

## Retrofit + Koin Usage

```kotlin
// build.gradle.kts
debugImplementation("com.peekaboo:inspector-debug:1.0.0")
debugImplementation("com.peekaboo:inspector-koin:1.0.0")
releaseImplementation("com.peekaboo:inspector-noop:1.0.0")
```

```kotlin
// Application.kt
startKoin {
    androidContext(this@App)
    modules(networkInspectorModule, appModule)
}

// appModule
val appModule = module {
    single { OkHttpClient.Builder().withNetworkInspector().build() }
    single { Retrofit.Builder().client(get())/* ... */.build() }
}
```

---

## Retrofit without Koin Usage

```kotlin
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(NetworkInspectorProvider.instance.getOkHttpInterceptor())
    .build()
```

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

Alternatively, click **"Create Mock from this request"** in the request detail view to pre-fill the form.

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
├── app/                     # Sample app (Retrofit + Koin)
│   ├── src/debug/           # Debug-only Application class (uses inspector-koin)
│   └── src/release/         # Release Application class (no inspector)
│
├── inspector-core/          # Shared interfaces & data models
│   ├── NetworkInspector     # Interface
│   ├── NetworkInspectorProvider  # Singleton accessor
│   ├── CapturedRequest      # Request/response data class
│   └── MockRule             # Mock rule data class
│
├── inspector-debug/         # Real implementation (debugImplementation only)
│   ├── CaptureInterceptor   # OkHttp interceptor that captures traffic
│   ├── NetworkStore         # In-memory ring buffer (max 500 entries)
│   ├── MockRepository       # Thread-safe mock rule storage
│   ├── InspectorServer      # Ktor embedded HTTP + WebSocket server
│   ├── RealNetworkInspector # NetworkInspector implementation
│   └── DebugInspectorInitializer  # ContentProvider auto-init
│
├── inspector-noop/          # No-op implementation (releaseImplementation only)
│   ├── NoOpNetworkInspector # All methods are no-ops
│   └── NoOpInspectorInitializer   # ContentProvider that sets up no-op
│
└── inspector-koin/          # Koin auto-injection module (debugImplementation)
    ├── networkInspectorModule     # Koin module with OkHttpClient.Builder
    └── withNetworkInspector()     # OkHttpClient.Builder extension
```

**Data flow:**

```
OkHttp request
    → CaptureInterceptor
        → MockRepository.findMatchingRule()
            ├── match found → return mock response → NetworkStore.add()
            └── no match   → chain.proceed() → NetworkStore.add()
                                                     ↓
                                             WebSocket push to browser
                                             REST API polling from browser
```

---

## License

MIT
