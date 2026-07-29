# Contributing to Decoy

Thanks for helping improve Decoy. This document covers the branching model and release
process. For architecture and build details see [`CLAUDE.md`](CLAUDE.md); the web UI's
design language is documented in [`docs/DESIGN.md`](docs/DESIGN.md).

## Branching model

- **`main`** — release-only. Always equals the last published release. No direct commits;
  the only thing that lands here is a `develop` → `main` merge at release time.
- **`develop`** — integration branch. All feature work and dependency updates target
  `develop`. Dependabot opens its PRs against `develop` (`target-branch: develop` in
  `.github/dependabot.yml`).

```
feature/* ─PR─▶ develop ─(accumulate, CI-gated)─▶ PR ─▶ main ─tag vX.Y.Z─▶ publish
```

## Development workflow

1. Branch off `develop` (`feature/…`, `fix/…`, `chore/…`).
2. Open a PR **into `develop`**. CI (`.github/workflows/ci.yml`) runs unit tests, assembles
   debug + release, and verifies the release APK contains no inspector code.
3. Keep public API changes mirrored between each real module and its `-noop` twin (see
   `CLAUDE.md`).

## Releasing (maintainers)

The full procedure — credentials setup, snapshot publishing from `develop`, and the
step-by-step release flow — lives in [`docs/RELEASING.md`](docs/RELEASING.md). Summary:

1. Release commit on `develop`: bump `VERSION_NAME` (drop `-SNAPSHOT`), move the
   CHANGELOG `[Unreleased]` section to `## [X.Y.Z] - date`, bump the README install
   snippets (both languages).
2. Merge `develop` → `main`, then tag **on main** (`vX.Y.Z`, must equal `VERSION_NAME`)
   and push the tag.
3. `publish.yml` stages the artifacts to Maven Central (**fails fast on a
   tag/`VERSION_NAME` mismatch**; never reuse a tag) and creates the GitHub Release from
   the CHANGELOG section. Finish the staged release in the
   [Central Portal](https://central.sonatype.com/).
4. Bump `develop` to the next `-SNAPSHOT` version.

### Required repository secrets

`publish.yml` needs these configured in **Settings → Secrets and variables → Actions**:

- `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`
- `SIGNING_KEY` (in-memory GPG key), `SIGNING_KEY_PASSWORD`

### Recommended branch protection

Protect `main` to require the CI check to pass before merging. Because release tags are cut
from `main`, this keeps every published commit test-gated (the publish workflow itself does
not re-run tests).

## Compatibility note

Decoy is a library: raising `projectCompileSdk` / AGP propagates to consumer apps through AAR
metadata. Keep the toolchain on a widely-supported baseline; a dependency bump that demands a
brand-new compileSdk/AGP is a migration to plan deliberately, not a routine merge.
