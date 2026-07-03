plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.peekaboo.ktor"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}
kotlin { explicitApi() }
dependencies {
    // The adapter pulls in the inspector server + web UI transitively, so apps
    // only ever declare this single artifact (plus its no-op twin for release).
    api(project(":peekaboo-android"))
    api(libs.ktor.client.core)
}
