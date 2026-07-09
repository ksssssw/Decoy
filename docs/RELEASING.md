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

## Cutting a release

1. Bump `VERSION_NAME` in the root `gradle.properties`, commit.
2. Tag and push:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
3. The **Publish** workflow uploads and *stages* the release. Finish it
   manually at <https://central.sonatype.com> (Deployments → verify → Publish).

To publish from your machine instead:

```bash
./gradlew publishToMavenCentral --no-configuration-cache
```

Releases are staged (not auto-released) so you can verify before the artifacts
go live.
