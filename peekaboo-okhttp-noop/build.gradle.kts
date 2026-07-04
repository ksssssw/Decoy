plugins {
    id("peekaboo.kotlin.jvm")
}
dependencies {
    // Ships PeekabooLauncher (returns null) so main-source-set call sites compile in release.
    api(project(":peekaboo-core"))
    api(libs.okhttp)
}
