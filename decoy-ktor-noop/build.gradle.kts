plugins {
    id("decoy.kotlin.jvm")
    id("decoy.publish")
}
description = "Decoy Ktor no-op — no-op stub for release builds (no server/intercept code)"
dependencies {
    // Re-exports :decoy-core (DecoyLauncher etc. return null/no-op in release)
    // so main-source-set call sites compile unchanged.
    api(project(":decoy-core"))
    api(libs.ktor.client.core)
}
