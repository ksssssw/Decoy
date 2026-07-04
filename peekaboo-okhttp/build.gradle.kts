plugins {
    id("peekaboo.android.library")
}
android {
    namespace = "com.peekaboo.okhttp"
}
dependencies {
    // The adapter pulls in the inspector server + web UI transitively, so apps
    // only ever declare this single artifact (plus its no-op twin for release).
    api(project(":peekaboo-android"))
    api(libs.okhttp)

    testImplementation(libs.okhttp.mockwebserver)
}
