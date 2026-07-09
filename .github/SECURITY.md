# Security Policy

## Reporting a Vulnerability

Please **do not** open a public issue for security problems.

Report vulnerabilities privately via GitHub's
[**Report a vulnerability**](https://github.com/ksssssw/Decoy/security/advisories/new)
(Security → Advisories). We aim to acknowledge reports within a few days and
will coordinate a fix and disclosure with you.

## Supported Versions

Decoy is pre-1.0; only the latest released version receives security fixes.

| Version | Supported |
|---------|-----------|
| 0.1.x   | ✅        |
| < 0.1   | ❌        |

## Scope & Threat Model

Decoy is a **debug-only** tool — the real (server + intercept) artifacts are
meant to ship via `debugImplementation`, and release builds get the `-noop`
twins, so no server/intercept code exists in a release APK. See the
[Security section of the README](../README.md#security) for the full model:

- The inspector server binds to **127.0.0.1 only** (loopback); PC access
  requires `adb forward` (USB/adb authorization).
- Credential headers (`Authorization`, `Cookie`, …) are **masked at capture
  time** and never reach the store, API, or UI in clear text.
- The `/ws` live feed rejects cross-origin WebSocket connections.
- Residual risk: another app on the **same device** could reach the debug
  build's local port (read captures, inject/wipe mock rules). Be mindful of who
  receives debug builds if your app handles sensitive traffic.

Reports about the loopback-port residual risk above are known and documented;
we're most interested in issues that break the debug/release split (e.g.
inspector code leaking into a release build) or the header-redaction / origin
checks.
