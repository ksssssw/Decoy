plugins {
    id("peekaboo.kotlin.jvm")
}
dependencies {
    // Re-exports :peekaboo-core (PeekabooLauncher etc. return null/no-op in release)
    // so main-source-set call sites compile unchanged.
    api(project(":peekaboo-core"))
    api(libs.ktor.client.core)
}
