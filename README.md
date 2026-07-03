# Peekaboo — Android Network Inspector & Mocker

**English** | [한국어](README.ko.md)

A Chucker/LeakCanary-style debug tool for Android. Add **two lines per HTTP stack** and inspect/mock all your app's traffic in a built-in web UI — perfect for reproducing error screens and edge-case data during development and QA.

- Automatically captures every HTTP request/response in your app
- **Mock responses** from the web UI with regex URL patterns (status code / body / headers / delay)
- Rule **grouping** (per screen / test case) with group and master on/off switches
- **Drag & drop** to reorder rules, move them between groups, reorder groups, and rename groups inline
- **Order-based matching** — the topmost rule in the list wins (drag to adjust precedence when rules overlap)
- Rule set **Export/Import** — share JSON files with teammates and designers; export everything, a single group, or hand-picked rules; **Undo** right after an import
- Dark/light theme, shows the running app's package & version
- Mock rules persist to a file — order and groups survive app restarts
- Supports both Retrofit (OkHttp) and Ktor client; DI-agnostic (Hilt/Koin/manual)
- ContentProvider auto-init — no `Application` code changes needed
- **Release builds contain no server/intercept code at all** (no-op swap)

---

## Quick Start

### Retrofit / OkHttp apps

```kotlin
// build.gradle.kts
debugImplementation("com.peekaboo:peekaboo-okhttp:<version>")
releaseImplementation("com.peekaboo:peekaboo-okhttp-noop:<version>")
```

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(PeekabooInterceptor()) // debug: capture+mock / release: no-op
    .build()
```

### Ktor client apps

```kotlin
// build.gradle.kts
debugImplementation("com.peekaboo:peekaboo-ktor:<version>")
releaseImplementation("com.peekaboo:peekaboo-ktor-noop:<version>")
```

```kotlin
HttpClient(CIO) {
    installPeekaboo()                      // debug: capture+mock / release: no-op
    install(ContentNegotiation) { gson() } // install Peekaboo BEFORE ContentNegotiation!
}
```

That's it. The debug artifact transitively brings the inspector server + web UI and starts automatically when the app launches.

### Opening the inspector

The server binds to the device's **loopback (127.0.0.1) only**. To connect:

| Where | How |
|---|---|
| PC browser (recommended) | `adb forward tcp:8090 tcp:8090` → `http://localhost:8090` |
| On-device browser | `http://localhost:8090` (or launch an intent via `PeekabooLauncher.getInspectorUrl()`) |

If port 8090 is taken, it automatically falls back to 8091–8099; the actual port is printed to Logcat (tag `Peekaboo`).

```kotlin
// Get the inspector URL from inside the app — returns null in release
val url: String? = PeekabooLauncher.getInspectorUrl()
```

---

## Integrating into an existing NetworkModule

Real services already have a DI module that owns their HTTP clients. Peekaboo is designed to drop into that module with **one line per stack** — no other structural change. The sample app's [`NetworkModule.kt`](app/src/main/kotlin/com/ksssssw/peekaboo/NetworkModule.kt) demonstrates the full pattern.

### Hilt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(PeekabooInterceptor()) // ← the only Peekaboo line
            .build()
}
```

### Koin

```kotlin
val networkModule = module {
    single {
        HttpClient(CIO) {
            installPeekaboo()                      // ← the only Peekaboo line
            install(ContentNegotiation) { gson() }
        }
    }
}
```

### Manual DI / no framework

```kotlin
object Network {
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(PeekabooInterceptor())     // ← the only Peekaboo line
        .build()
}
```

Because the `-noop` artifacts expose identical packages and signatures, these call sites compile unchanged in every build type — the debug/release behavior is decided **entirely by the Gradle dependency swap** (`debugImplementation` real / `releaseImplementation` noop). Never use plain `implementation` for the real artifact: that would ship an open-port server in your release build.

---

## Module structure

```
:peekaboo-okhttp        OkHttp interceptor (brings server+web UI transitively)  ← debugImplementation
:peekaboo-okhttp-noop   proceed-only stub                                       ← releaseImplementation
:peekaboo-ktor          Ktor client plugin (brings server+web UI transitively)  ← debugImplementation
:peekaboo-ktor-noop     no-op stub                                              ← releaseImplementation
:peekaboo-core          pure JVM, zero deps — models/stores/launcher (no direct declaration needed)
:peekaboo-android       inspector server + web UI + auto-init (no direct declaration needed)
:app                    sample app — demonstrates both the Retrofit and Ktor stacks
```

The no-op artifacts provide the same packages/signatures as the real modules, so your `main` source set keeps its Peekaboo call sites as-is and the dependency swap alone fully neutralizes them in release.

## Mock Rules

Create rules in the web UI's **Mock Rules** tab, or from a captured request via **Create Mock from Request** in the Traffic detail view.

| Field | Description |
|---|---|
| URL Pattern | Regex. Applied when it matches any part of the URL (`containsMatchIn`) |
| Method | GET/POST/… or ANY |
| Status Code / Body / Headers | The mocked response content |
| Delay | Response delay in ms — reproduces slow networks |
| Description | Shown in the rule list — write it so designers/PMs understand what this mocks |
| Group | Per-screen / per-test-case group. Toggle a whole group with the switch in its header |

**Matching precedence is list order** — when multiple rules match the same request, the topmost enabled rule wins (flagged with a DUP badge). With drag & drop you can:

- Drag rules up/down to reorder, or drop them onto another group (or its header) to move them
- Drop two ungrouped rules onto each other to create a new group (name it right away)
- Drag group headers to reorder groups; rename with the pencil icon (renaming onto an existing name merges)

Rules are saved — including order and groups — to `files/peekaboo/rules.json` and survive app restarts. Captured traffic is in-memory (latest 500 entries).

Mocked calls show a purple duration in the Traffic list. The duration is the **actual elapsed time** (including the configured delay); the detail view additionally shows the configured delay as `Mock Delay`.

### Sharing rule sets (Export / Import)

Use **Export** in the Mock Rules tab to download rules as a JSON file — either everything, or a subset picked in the export dialog's checkbox tree. Each group header also has a quick per-group export button. On the receiving side, **Import** offers **Merge** (keep current rules, add imported) or **Replace** (wipe first). Right after an import, an **Undo** button in the toast restores the pre-import state — until the next rule change. The file format is identical to the on-device `rules.json`, so they're interchangeable. Rules with invalid regexes are skipped individually and counted.

## REST API

The API used by the web UI can also be called directly (e.g. injecting rules from CI).

| Method | Path | Description |
|---|---|---|
| GET | `/api/calls` | List captured requests |
| GET | `/api/calls/{id}` | Get one request |
| DELETE | `/api/calls` | Clear captures |
| GET / POST | `/api/mocks` | List / create rules (invalid regex → 400) |
| PUT / DELETE | `/api/mocks/{id}` | Update / delete a rule |
| PATCH | `/api/mocks/{id}/toggle` | Toggle a rule |
| PATCH | `/api/mocks/group/toggle` | Toggle a whole group — `{"group":"...","isEnabled":true}` |
| PATCH | `/api/mocks/group/rename` | Rename a group — `{"from":"...","to":"..."}` (merges if the target name exists) |
| PATCH | `/api/mocks/all/toggle` | Toggle all rules — `{"isEnabled":false}` |
| PUT | `/api/mocks/layout` | Save the full order/group layout — `{"items":[{"id":"...","group":"..."},…]}` (order = matching precedence) |
| POST | `/api/mocks/import` | Bulk-add rules — `{"mode":"merge"\|"replace","rules":[…]}` |
| GET | `/api/status` | Server status + host app info (package/version/device) |
| WS | `/ws` | Real-time push of new captures |

---

## Security

- The server binds to **127.0.0.1 only** — other devices on the same Wi-Fi cannot reach it. PC access works only through `adb forward` (requires USB/adb authorization).
- Release builds include only the no-op artifacts, so no server/intercept code exists in the APK.
- Residual threat model: **another app installed on the same device** could reach the debug build's local port (same as Chucker and similar tools). If your app handles sensitive traffic, be mindful of who receives debug builds.
- Never add the real artifact with `implementation` (all build types) — your release would ship an open-port server.

### Verifying release builds contain no Peekaboo

```bash
./gradlew :app:assembleRelease

# 1) The dex must contain no server classes (only core models + stubs)
$ANDROID_HOME/build-tools/<ver>/dexdump classes.dex from app-release.apk | grep "com/peekaboo"
#   → only com/peekaboo/core/* and com/peekaboo/okhttp/PeekabooInterceptor (stub) should appear
#   → com/peekaboo/android/* and io/ktor/server/* must be absent

# 2) No listening port after install (8090 = 0x1F9A)
adb shell "cat /proc/net/tcp | grep 1F9A"   # no output is correct
```

---

## Sample app

The `:app` module demonstrates both the Retrofit (OkHttp) and Ktor client paths on one screen.

1. `./gradlew :app:installDebug` and launch
2. `adb forward tcp:8090 tcp:8090` → open `http://localhost:8090` on your PC
3. Press the GET/POST/404/delay buttons and watch the traffic
4. Create a rule in Mock Rules (e.g. `/posts` with 500 + 3000ms) → call again from the app → verify the error/delay behavior
5. Force-stop and relaunch the app — the rules should survive

## Roadmap

- Server-side request replay (re-issuing requests through the app's real client configuration)
- Ktor 3.x support (removing `MockCallFactory`'s InternalAPI dependency)
- Maven Central publication

## License

MIT
