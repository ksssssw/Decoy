# Contributing to Decoy

Thanks for helping improve Decoy. This document covers the branching model and release
process. For architecture and build details see [`CLAUDE.md`](CLAUDE.md).

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

Releases are cut from `main` and published to Maven Central via
`.github/workflows/publish.yml` (staged; finished manually in the Central Portal).

1. Bump `VERSION_NAME` in `gradle.properties` to the target version (e.g. `0.2.0`) in a
   release commit on `develop`.
2. Merge `develop` → `main`.
3. Tag **on main** with a matching `v` prefix and push it:
   ```bash
   git checkout main && git pull
   git tag v0.2.0        # must equal VERSION_NAME
   git push origin v0.2.0
   ```
4. The tag triggers `publish.yml`. It **fails fast if the tag doesn't match `VERSION_NAME`**
   — this guard keeps the git tag and the published artifact version identical. Maven Central
   forbids re-publishing a released version, so never reuse a tag.
5. Finish the staged release in the [Central Portal](https://central.sonatype.com/).

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
