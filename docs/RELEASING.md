# Releasing

Decoy publishes six library artifacts to Maven Central (Central Portal) under
the group `io.github.ksssssw`:

`decoy-core`, `decoy-android`, `decoy-okhttp`, `decoy-okhttp-noop`,
`decoy-ktor`, `decoy-ktor-noop`.

Publishing is wired through the `decoy.publish` convention plugin
(`build-logic/convention/src/main/kotlin/PublishingConventionPlugin.kt`) using
the [vanniktech maven-publish plugin](https://github.com/vanniktech/gradle-maven-publish-plugin).
The version comes from `VERSION_NAME` in the root `gradle.properties`.

## One-time setup

### Credentials (never commit these)

Locally, put them in `~/.gradle/gradle.properties`:

```properties
mavenCentralUsername=<Central Portal user token name>
mavenCentralPassword=<Central Portal user token password>
signingInMemoryKey=<ASCII-armored GPG secret key>
signingInMemoryKeyPassword=<GPG key passphrase>
```

- **Central Portal token**: generate at <https://central.sonatype.com> →
  Account → *Generate User Token* (this is a token, not your login password).
- **GPG key**: `gpg --export-secret-keys --armor <KEY_ID>` — paste the entire
  output including the `-----BEGIN/END PGP PRIVATE KEY BLOCK-----` lines. The
  public key must be uploaded to a keyserver (e.g. `keys.openpgp.org`).

### CI secrets (for the `Publish` workflow)

Add these under **Settings → Secrets and variables → Actions**:

| Secret | Maps to Gradle property |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | `mavenCentralUsername` |
| `MAVEN_CENTRAL_PASSWORD` | `mavenCentralPassword` |
| `SIGNING_KEY` | `signingInMemoryKey` (full ASCII-armored key) |
| `SIGNING_KEY_PASSWORD` | `signingInMemoryKeyPassword` |

`.github/workflows/publish.yml` maps each to `ORG_GRADLE_PROJECT_<name>`.

## Publishing a snapshot (pre-release testing)

Snapshots are published **from `develop`**, before merging to `main` — use them
to verify the artifacts in a real consumer app ahead of the actual release.

1. Make sure `VERSION_NAME` in `gradle.properties` ends with `-SNAPSHOT`
   (e.g. `0.2.0-SNAPSHOT` — the norm on `develop` during a release cycle).
2. Run the **Publish** workflow manually: GitHub → Actions → Publish →
   *Run workflow* → branch `develop`. The vanniktech plugin auto-detects the
   `-SNAPSHOT` suffix and uploads to the Central Portal **snapshots**
   repository — no staging, no manual finish, immediately consumable.
   (The workflow refuses a manual dispatch when `VERSION_NAME` is a release
   version, and refuses release tags pointing at a `-SNAPSHOT`.)
3. In the consumer app, add the snapshots repository and depend on the
   snapshot version:
   ```kotlin
   // settings.gradle.kts
   dependencyResolutionManagement {
       repositories {
           maven("https://central.sonatype.com/repository/maven-snapshots/")
       }
   }
   // build.gradle.kts
   debugImplementation("io.github.ksssssw:decoy-okhttp:0.2.0-SNAPSHOT")
   releaseImplementation("io.github.ksssssw:decoy-okhttp-noop:0.2.0-SNAPSHOT")
   ```

Snapshots are mutable (re-dispatching overwrites the same version) and expire
on the Portal after a retention period — never reference them from a released
app.

## Cutting a release

Releases are cut **from `main`** (see the branch flow in
[CONTRIBUTING.md](../CONTRIBUTING.md) — `main` is release-only and must equal
the last published release).

1. **Release commit on `develop`:**
   - Bump `VERSION_NAME` in the root `gradle.properties` to the release version
     (drop the `-SNAPSHOT` suffix, e.g. `0.2.0`).
   - In `CHANGELOG.md`, move the `[Unreleased]` entries into a new
     `## [X.Y.Z] - YYYY-MM-DD` section and update the compare links at the
     bottom. The publish workflow extracts exactly this section (by heading
     match) as the GitHub Release notes.
   - Update the install-snippet versions in `README.md` **and** `README.ko.md`.
2. Merge `develop` → `main` (PR).
3. Tag **on `main`** and push — the tag must equal `VERSION_NAME`:
   ```bash
   git checkout main && git pull
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```
4. The **Publish** workflow runs on the tag:
   - It **fails fast if the tag doesn't match `VERSION_NAME`** (or if the
     version still ends in `-SNAPSHOT`) — Maven Central forbids re-publishing
     a released version, so never reuse a tag.
   - It uploads and *stages* all six artifacts. Finish the release manually at
     <https://central.sonatype.com> (Deployments → verify → Publish).
   - After the staging upload succeeds, a second job creates the **GitHub
     Release** using the version's `CHANGELOG.md` section as the notes.
5. Back on `develop`, bump `VERSION_NAME` to the next `-SNAPSHOT`
   (e.g. `0.3.0-SNAPSHOT`).

To publish from your machine instead (same credentials, from
`~/.gradle/gradle.properties`):

```bash
./gradlew publishToMavenCentral --no-configuration-cache
```

Releases are staged (not auto-released) so you can verify before the artifacts
go live.
