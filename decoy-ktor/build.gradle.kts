plugins {
    id("decoy.android.library")
    id("decoy.publish")
}
description = "Decoy Ktor — capturing/mocking plugin for the Ktor client (debug artifact)"
android {
    namespace = "com.decoy.ktor"
}
dependencies {
    // The adapter pulls in the inspector server + web UI transitively, so apps
    // only ever declare this single artifact (plus its no-op twin for release).
    api(project(":decoy-android"))
    api(libs.ktor.client.core)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.gson)
}
