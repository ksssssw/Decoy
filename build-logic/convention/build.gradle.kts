plugins {
    `kotlin-dsl`
}

group = "com.peekaboo.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinJvm") {
            id = "peekaboo.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("androidLibrary") {
            id = "peekaboo.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "peekaboo.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
