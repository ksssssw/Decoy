plugins {
    id("decoy.android.library")
    id("decoy.publish")
}
description = "Decoy OkHttp — capturing/mocking interceptor for OkHttp/Retrofit (debug artifact)"
android {
    namespace = "com.decoy.okhttp"
}
dependencies {
    // The adapter pulls in the inspector server + web UI transitively, so apps
    // only ever declare this single artifact (plus its no-op twin for release).
    api(project(":decoy-android"))
    api(libs.okhttp)

    testImplementation(libs.okhttp.mockwebserver)
}
