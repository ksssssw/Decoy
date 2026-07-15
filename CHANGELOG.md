# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- Multi-instance inspector usability: browser tabs are now distinguishable (app label in the page title, header, and `/api/status`), the server rebinds its previous port after an app restart (`SO_REUSEADDR`) instead of drifting to 8091+, and Logcat startup lines name the owning app and package. (#12)

### Changed
- Consumer-facing dependencies are pinned to the minimum supported versions (Kotlin 2.2.20, OkHttp 4.12.0, Ktor 3.3.0) so published artifacts never force-upgrade consuming apps; Dependabot no longer bumps them automatically. (#14)

### Added
- Tag-triggered publishing workflow that stages all library artifacts to Maven Central when a `vX.Y.Z` tag is pushed. (#1)

## [0.1.0] - 2026-07-07

Initial release, published to Maven Central under `io.github.ksssssw`.

### Added
- Debug-only network inspector & mocker for Android with a self-contained web UI, served on-device at `127.0.0.1:8090` (falls back up to +10) — open it via `adb forward tcp:8090 tcp:8090`.
- Automatic HTTP traffic capture into an in-memory ring buffer (latest 500 requests) with live updates over WebSocket; bodies are capped at 1 MB.
- Regex-based mock rules with per-rule enable/disable, drag-and-drop priority ordering, response delay, and crash-safe persistence to `filesDir/decoy/rules.json`.
- OkHttp support via `decoy-okhttp` (`DecoyInterceptor`).
- Ktor client support via `decoy-ktor` (`installDecoy()`).
- No-op twins `decoy-okhttp-noop` / `decoy-ktor-noop` with identical public APIs, so release builds compile against the same call sites but contain no server or interception code.
- Zero-setup auto-init via ContentProvider — no `Application` code required; initialization failures never crash the host app.
- Published modules: `decoy-core`, `decoy-android`, `decoy-okhttp`, `decoy-okhttp-noop`, `decoy-ktor`, `decoy-ktor-noop`.

[Unreleased]: https://github.com/ksssssw/Decoy/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ksssssw/Decoy/releases/tag/v0.1.0
