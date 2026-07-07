plugins {
    id("decoy.android.application")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ksssssw.decoy"

    defaultConfig {
        applicationId = "com.ksssssw.decoy"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Decoy — 2 lines per HTTP stack. The debug artifact brings the inspector
    // server + web UI transitively; the no-op twin keeps release call sites compiling.
    debugImplementation(project(":decoy-ktor"))
    releaseImplementation(project(":decoy-ktor-noop"))
    debugImplementation(project(":decoy-okhttp"))
    releaseImplementation(project(":decoy-okhttp-noop"))

    // Ktor client
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.gson)

    // Retrofit (OkHttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.coroutines)
}
