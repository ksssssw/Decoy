# Decoy — Android Network Inspector & Mocker

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ksssssw/decoy-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.ksssssw/decoy-core)
[![CI](https://github.com/ksssssw/Decoy/actions/workflows/ci.yml/badge.svg)](https://github.com/ksssssw/Decoy/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?logo=kotlin)

**English** | [한국어](README.ko.md)

<!-- TODO: hero screenshot/GIF of the web inspector UI goes here -->
<!-- e.g. <p align="center"><img src="docs/images/inspector.gif" width="720" alt="Decoy inspector"></p> -->

A debug-only network inspector & mocker for Android. Add **two lines per HTTP stack** and inspect/mock all your app's traffic in a built-in web UI — perfect for reproducing error screens and edge-case data during development and QA. Release builds contain none of it.

- Automatically captures every HTTP request/response in your app
- **Mock responses** from the web UI with regex URL patterns (status code / body / headers / delay)
- Rule **grouping** (per screen / test case) with group and master on/off switches
- **Drag & drop** to reorder rules, move them between groups, reorder groups, and rename groups inline
- **Order-based matching** — the topmost rule in the list wins (drag to adjust precedence when rules overlap)
- Rule set **Export/Import** — share JSON files with teammates and designers; export everything, a single group, or hand-picked rules; **Undo** right after an import
- Dark/light theme, shows the running app's package & version
- Mock rules persist to a file — order and groups survive app restarts
- Supports both Retrofit (OkHttp) and Ktor 3.x client; DI-agnostic (Hilt/Koin/manual)
- ContentProvider auto-init — no `Application` code changes needed
- **Release builds contain no server/intercept code at all** (no-op swap)

---

## Quick Start

### Retrofit / OkHttp apps

```kotlin
// build.gradle.kts
debugImplementation("io.github.ksssssw:decoy-okhttp:0.1.0")
releaseImplementation("io.github.ksssssw:decoy-okhttp-noop:0.1.0")
```

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(DecoyInterceptor()) // debug: capture+mock / release: no-op
    .build()
```

### Ktor client apps

```kotlin
// build.gradle.kts
debugImplementation("io.github.ksssssw:decoy-ktor:0.1.0")
releaseImplementation("io.github.ksssssw:decoy-ktor-noop:0.1.0")
```

```kotlin
HttpClient(CIO) {
    installDecoy()                      // debug: capture+mock / release: no-op
    install(ContentNegotiation) { gson() } // install Decoy BEFORE ContentNegotiation!
}
```

> **Ktor version:** Decoy targets **Ktor 3.x** (tested on 3.3). Because Gradle unifies the Ktor version across your debug classpath, an app still on Ktor 2.x is not supported — bump to Ktor 3.x first. Any engine works (`CIO`, `OkHttp`, …); `installDecoy()` is engine-agnostic.

That's it. The debug artifact transitively brings the inspector server + web UI and starts automatically when the app launches.

### Opening the inspector

The server binds to the device's **loopback (127.0.0.1) only**. To connect:

| Where | How |
|---|---|
| PC browser (recommended) | `adb forward tcp:8090 tcp:8090` → `http://localhost:8090` |
| On-device browser | `http://localhost:8090` (or launch an intent via `DecoyLauncher.getInspectorUrl()`) |

If port 8090 is taken, it automatically falls back to 8091–8099; the actual port is printed to Logcat (tag `Decoy`). The server binds with `SO_REUSEADDR`, so an app restart re-acquires its previous port even while old connections linger in TIME_WAIT — an already-issued `adb forward` keeps working across restarts.

```kotlin
// Get the inspector URL from inside the app — returns null in release
val url: String? = DecoyLauncher.getInspectorUrl()
```

### Multiple apps or devices

Each Decoy-enabled app on a device gets its own port: the first to launch binds 8090, the next 8091, and so on (launch order decides). To see which app owns which port:

```bash
adb logcat -s Decoy
# Decoy: Inspector for My App (com.example.myapp) running at http://localhost:8090 (loopback only)
# Decoy: Inspector for Other App (com.example.other) running at http://localhost:8091 (loopback only)
```

Forward each port you need (`adb forward --list` shows current mappings). With several devices/emulators, target each by serial — the host port doesn't have to match the device port:

```bash
adb devices                                        # list serials
adb -s emulator-5554 forward tcp:8090 tcp:8090
adb -s R3CX90ABCDE forward tcp:8091 tcp:8090       # host :8091 → device :8090
```

Every browser tab titles itself with the app's name and the host-side port (e.g. `My App · Decoy :8091`), so tabs stay distinguishable.

### Troubleshooting: inspector unreachable (e.g. the next morning)

`adb forward` mappings go stale when the device sleeps, reconnects, or the host-side adb daemon wedges — the SDK on the device can't fix that end. Recover in this order:

```bash
adb forward --list                       # is the mapping still there?
adb forward --remove-all                 # drop stale mappings…
adb forward tcp:8090 tcp:8090            # …and re-issue
adb kill-server && adb start-server      # last resort: restart the adb daemon (then re-forward)
```

The app side needs nothing: restarting the app no longer changes its port (see `SO_REUSEADDR` above), so a re-issued forward to the same port just works.

---

## Integrating into an existing NetworkModule

Real services already have a DI module that owns their HTTP clients. Decoy is designed to drop into that module with **one line per stack** — no other structural change. The sample app's [`NetworkModule.kt`](app/src/main/kotlin/com/ksssssw/decoy/NetworkModule.kt) demonstrates the full pattern.

### Hilt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(DecoyInterceptor()) // ← the only Decoy line
            .build()
}
```

### Koin

```kotlin
val networkModule = module {
    single {
        HttpClient(CIO) {
            installDecoy()                      // ← the only Decoy line
            install(ContentNegotiation) { gson() }
        }
    }
}
```

### Manual DI / no framework

```kotlin
object Network {
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(DecoyInterceptor())     // ← the only Decoy line
        .build()
}
```

Because the `-noop` artifacts expose identical packages and signatures, these call sites compile unchanged in every build type — the debug/release behavior is decided **entirely by the Gradle dependency swap** (`debugImplementation` real / `releaseImplementation` noop). Never use plain `implementation` for the real artifact: that would ship an open-port server in your release build.

---

## Module structure

```
:decoy-okhttp        OkHttp interceptor (brings server+web UI transitively)  ← debugImplementation
:decoy-okhttp-noop   proceed-only stub                                       ← releaseImplementation
:decoy-ktor          Ktor client plugin (brings server+web UI transitively)  ← debugImplementation
:decoy-ktor-noop     no-op stub                                              ← releaseImplementation
:decoy-core          pure JVM, zero deps — models/stores/launcher (no direct declaration needed)
:decoy-android       inspector server + web UI + auto-init (no direct declaration needed)
:app                    sample app — demonstrates both the Retrofit and Ktor stacks
```

The no-op artifacts provide the same packages/signatures as the real modules, so your `main` source set keeps its Decoy call sites as-is and the dependency swap alone fully neutralizes them in release.

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

Rules are saved — including order and groups — to `files/decoy/rules.json` and survive app restarts. Captured traffic is in-memory (latest 500 entries).

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
- Credential headers (`Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`) are **masked as `[redacted]` at capture time** — tokens never reach the store, the API, or the web UI in clear text.
- The `/ws` live feed **rejects cross-origin WebSocket connections** (only `localhost`/`127.0.0.1` origins, or clients without an Origin header, are accepted) — a page open in the device browser cannot read the capture stream.
- Release builds include only the no-op artifacts, so no server/intercept code exists in the APK.
- Residual threat model: **another app installed on the same device** could reach the debug build's local port. Beyond reading captures, that includes **injecting or wiping mock rules via the REST API** — i.e. altering the responses your debug app sees. If your app handles sensitive traffic, be mindful of who receives debug builds.
- Never add the real artifact with `implementation` (all build types) — your release would ship an open-port server.

### Verifying release builds contain no Decoy

```bash
./gradlew :app:assembleRelease

# 1) The dex must contain no server classes (only core models + stubs)
$ANDROID_HOME/build-tools/<ver>/dexdump classes.dex from app-release.apk | grep "com/decoy"
#   → only com/decoy/core/* and com/decoy/okhttp/DecoyInterceptor (stub) should appear
#   → com/decoy/android/* and io/ktor/server/* must be absent

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
- Decouple the embedded inspector server from the consumer's Ktor version (shade/relocate or a dependency-light server) so a single artifact serves apps on any Ktor version — or none

## Contributing

Branching model and release process are documented in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT
