plugins {
    id("decoy.android.library")
    id("decoy.publish")
}
description = "Decoy Android — inspector server, web UI, and ContentProvider auto-init"
android {
    namespace = "com.decoy.android"
    testOptions {
        // android.util.Log is called from JVM unit tests — return defaults instead of throwing
        unitTests.isReturnDefaultValues = true
    }
}
dependencies {
    api(project(":decoy-core"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.gson)
    implementation(libs.coroutines)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
}
