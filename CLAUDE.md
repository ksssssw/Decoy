# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Peekaboo is an Android network inspector & mocker SDK (Chucker-style debug tool). Apps add 2 Gradle lines per HTTP stack (real artifact as `debugImplementation`, no-op twin as `releaseImplementation`) and get a web UI that captures all HTTP traffic and serves regex-based mock rules. Release builds contain no server/intercept code — the debug/release split is done entirely by the Gradle dependency swap, never by runtime flags.

## Commands

```bash
./gradlew assembleDebug assembleRelease        # build all modules
./gradlew test                                  # all JVM unit tests (no Robolectric, no emulator needed)
./gradlew :peekaboo-core:test                   # tests for one module
./gradlew :peekaboo-core:test --tests "com.peekaboo.core.MockRepositoryTest"   # single test class

# Run the sample app and open the inspector
./gradlew :app:installDebug
adb forward tcp:8090 tcp:8090                   # then open http://localhost:8090

# Verify release contains no inspector code (security check from README)
./gradlew :app:assembleRelease                  # then dexdump | grep com/peekaboo — only com/peekaboo/core/* + stub interceptors may appear
```

## Module map & dependency flow

```
:app (sample, com.ksssssw.peekaboo)          — consumer example; debugImplementation(real) + releaseImplementation(noop)
:peekaboo-okhttp / :peekaboo-ktor            — Android libs: interceptor adapters, api(:peekaboo-android)
:peekaboo-okhttp-noop / :peekaboo-ktor-noop  — pure JVM stubs, api(:peekaboo-core) only
:peekaboo-android                            — Android lib: ContentProvider auto-init + Ktor CIO server + web UI
:peekaboo-core                               — pure Kotlin/JVM, zero deps: models, NetworkStore, MockRepository
```

- SDK code lives in `com.peekaboo.*`; the sample app in `com.ksssssw.peekaboo`.
- **No-op twins must mirror the real modules' FQNs and signatures exactly** (`com.peekaboo.okhttp.PeekabooInterceptor`, `installPeekaboo()`), so consumer call sites compile against either. Any public API change in `peekaboo-okhttp`/`peekaboo-ktor` must be replicated in its `-noop` twin.

## Build conventions (build-logic)

Convention plugins live in `build-logic/convention/src/main/kotlin/` (composite build, nowinandroid-style binary plugins). Module build files declare only a plugin id + namespace + deps:

- `peekaboo.kotlin.jvm` — kotlin.jvm, Java/Kotlin target 11, `explicitApi()`, junit + kotlin-test-junit
- `peekaboo.android.library` — android.library + kotlin.android, compileSdk/minSdk from catalog, `explicitApi()`, test deps
- `peekaboo.android.application` — android.application + kotlin.android, SDK levels (no explicitApi)

SDK levels are catalog versions in `gradle/libs.versions.toml` (`projectCompileSdk`/`projectMinSdk`/`projectTargetSdk`). The root `build.gradle.kts` `apply false` block must stay — it puts AGP/KGP/compose on the root classpath so the convention plugins' `compileOnly` deps resolve. All library modules compile with **explicit API mode**: every public declaration needs an explicit visibility modifier and return type.

## Runtime architecture

Auto-init → capture → serve, with no host-app code:

1. **`PeekabooInitializer`** (ContentProvider in `peekaboo-android`) starts everything at app launch, wrapped in `runCatching` — a debug tool must never crash the host app. It wires `FileRuleStorage` into `MockRepository`, starts `PeekabooServer`, and sets `PeekabooProvider.instance`.
2. **Interceptors** (`PeekabooInterceptor` for OkHttp, `PeekabooKtorPlugin` for Ktor) consult `MockRepository.findMatchingRule(url, method)` — first *enabled* rule in list order wins (order-based matching; UI drag-and-drop reorders). Match → synthetic response after `delayMs`; no match → proceed and record into `NetworkStore` (in-memory ring buffer, latest 500).
3. **`PeekabooServer`** — Ktor CIO bound to **127.0.0.1 only** (loopback is the security model; never bind wider), preferred port 8090 with fallback +10. Serves the web UI from classpath `resources/web/`, REST under `/api/*`, and pushes new captures over `/ws` via a `NetworkStore` listener. Route logic is extracted into `Application.peekabooModule(...)` so tests drive it with Ktor `testApplication` without a socket.
4. **Web UI** is a single hand-edited file: `peekaboo-android/src/main/resources/web/index.html` (~1700 lines, inline CSS/JS, no frontend build). Fully offline/self-contained — do not add CDN references.

## Gotchas

- **Gson bypasses Kotlin constructors** — deserialized `MockRule`s can hold nulls in non-null fields, and `copy()` on such an instance throws NPE. Never `copy()` a Gson-created rule; run it through `MockRule.sanitized()` first (see `MockRuleSanitizer.kt`; applied on load in `FileRuleStorage`). Same reason the server DTOs in `PeekabooServer.kt` use all-nullable fields with explicit defaults in `.toRule()`.
- **Ktor `@OptIn(InternalAPI)` is quarantined in `MockCallFactory.kt`** — the only file allowed to touch it, so a Ktor 3.x migration changes just that file. Don't spread InternalAPI usage.
- **Plugin order matters for Ktor consumers**: `installPeekaboo()` must be installed *before* ContentNegotiation (bodies are captured as `OutgoingContent`).
- **`peekaboo-android` unit tests rely on `unitTests.isReturnDefaultValues = true`** (set in that module only) because server/storage code calls `android.util.Log` directly. Don't move this into the convention plugin — it would silently change test semantics of other modules.
- `NetworkStore` and `MockRepository` are process-wide singletons — tests must clear them in setup/teardown (see `PeekabooServerRoutesTest`).
- Body capture caps at 1MB and must not consume the original stream (`peekBody` on OkHttp, `call.save()` on Ktor); event-stream/compressed/binary bodies are skipped with marker strings.
- Rule persistence (`filesDir/peekaboo/rules.json`) is crash-safe: write `.tmp` then rename. Keep that pattern when touching `FileRuleStorage`.
