plugins {
    id("peekaboo.android.library")
}
android {
    namespace = "com.peekaboo.android"
    testOptions {
        // android.util.Log is called from JVM unit tests — return defaults instead of throwing
        unitTests.isReturnDefaultValues = true
    }
}
dependencies {
    api(project(":peekaboo-core"))
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
