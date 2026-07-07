plugins {
    `kotlin-dsl`
}

group = "com.decoy.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    // Not on the root `plugins {}` classpath (unlike AGP/KGP), so it must be bundled
    // into the convention plugin's runtime classpath for `apply("com.vanniktech...")`.
    implementation(libs.vanniktech.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinJvm") {
            id = "decoy.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("androidLibrary") {
            id = "decoy.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "decoy.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("publish") {
            id = "decoy.publish"
            implementationClass = "PublishingConventionPlugin"
        }
    }
}
