# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Tag-triggered publishing workflow that stages all library artifacts to Maven Central when a `vX.Y.Z` tag is pushed, and creates the GitHub Release with this file's matching section as the release notes. (#1, #15)
- Snapshot publishing: manually dispatching the Publish workflow uploads `-SNAPSHOT` versions to the Central Portal snapshots repository (release tags refuse `-SNAPSHOT`; manual dispatches refuse release versions).

### Changed
- Web UI redesign: new light/dark design system, signal-fork brand mark, resizable traffic sidebar, maximizable/resizable rule editor, connection status chip, and refined empty states. (#25)
- Consumer-facing dependencies are pinned to the minimum supported versions (Kotlin 2.2.20, OkHttp 4.12.0, Ktor 3.3.0) so published artifacts never force-upgrade consuming apps; Dependabot no longer bumps them automatically. (#14)
- Header redaction now also masks common API-key/token headers (`X-Api-Key`, `X-Auth-Token`, …) and credential-like suffixes (`*-key`, `*-token`, `*-secret`, `*-auth`), not just the four classic credential headers.
- `decoy-ktor-noop`'s source file is renamed to `DecoyKtorPlugin.kt` so its JVM facade class matches the real module. **Java** call sites referencing `DecoyKtorNoopKt` must switch to `DecoyKtorPluginKt` (Kotlin callers are unaffected); previously such code compiled in debug and crashed in release.
- The inspector web UI is served from the namespaced classpath package `decoy-web/` so no other dependency's `web/` resources can collide into the served root.

### Fixed
- Multi-instance inspector usability: browser tabs are now distinguishable (app label in the page title, header, and `/api/status`), the server rebinds its previous port after an app restart (`SO_REUSEADDR`) instead of drifting to 8091+, and Logcat startup lines name the owning app and package. (#12)
- Decoy's own bookkeeping can no longer alter the host app's traffic: capture and mock-construction failures degrade to "not captured"/"not mocked" instead of turning a successful response into a client error, and a throwing capture listener no longer breaks recording.
- `Decoy.start()` is idempotent — a second call returns the bound port instead of orphaning the running server (leaked socket, port drift off an issued `adb forward`); out-of-range ports fall back to 8090 instead of throwing into the host app.
- Rule persistence fsyncs before its atomic rename (power loss can't leave an empty `rules.json`) and a failed save keeps the previous file instead of possibly deleting both.
- Readers can no longer observe an empty rule list mid-update (rules are an atomically swapped immutable snapshot).

### Security
- DNS-rebinding protection: every endpoint (REST, static UI, WebSocket) rejects requests whose `Host` is not local, and non-WebSocket requests with an untrusted `Origin`, with 403 — previously only `/ws` checked Origin.
- Mock rules are validated server-side (regex compilability and ≤1000-char length, `statusCode` in 100–599, `delayMs` in 0–60000, RFC 7230 header charset → 400) and sanitized on load, so a crafted or corrupted rule can't crash host-app requests (null-header NPE) or hang them — pathological regexes are deadline-bounded (100 ms) and quarantined.
- REST request bodies are capped at 10 MB (413); WebSocket sessions get ping/timeout, a 1 MB frame cap, and bounded backpressure; the capture ring buffer enforces a 32 MB total-size budget on top of the 500-entry cap.
- API error responses return proper 400/413/415 for client errors and no longer echo internal exception messages.

## [0.1.0] - 2026-07-07

Initial release, published to Maven Central under `io.github.ksssssw`.

### Added
- Debug-only network inspector & mocker for Android with a self-contained web UI, served on-device at `127.0.0.1:8090` (falls back to 8091–8099) — open it via `adb forward tcp:8090 tcp:8090`.
- Automatic HTTP traffic capture into an in-memory ring buffer (latest 500 requests) with live updates over WebSocket; bodies are capped at 1 MB.
- Regex-based mock rules with per-rule enable/disable, drag-and-drop priority ordering, response delay, and crash-safe persistence to `filesDir/decoy/rules.json`.
- OkHttp support via `decoy-okhttp` (`DecoyInterceptor`).
- Ktor client support via `decoy-ktor` (`installDecoy()`).
- No-op twins `decoy-okhttp-noop` / `decoy-ktor-noop` with identical public APIs, so release builds compile against the same call sites but contain no server or interception code.
- Zero-setup auto-init via ContentProvider — no `Application` code required; initialization failures never crash the host app.
- Published modules: `decoy-core`, `decoy-android`, `decoy-okhttp`, `decoy-okhttp-noop`, `decoy-ktor`, `decoy-ktor-noop`.

[Unreleased]: https://github.com/ksssssw/Decoy/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ksssssw/Decoy/releases/tag/v0.1.0
