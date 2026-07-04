plugins {
    id("peekaboo.android.library")
}
android {
    namespace = "com.peekaboo.ktor"
}
dependencies {
    // The adapter pulls in the inspector server + web UI transitively, so apps
    // only ever declare this single artifact (plus its no-op twin for release).
    api(project(":peekaboo-android"))
    api(libs.ktor.client.core)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.gson)
}
