plugins {
    alias(libs.plugins.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}
dependencies {
    // Ships PeekabooLauncher (returns null) so main-source-set call sites compile in release.
    api(project(":peekaboo-core"))
    api(libs.ktor.client.core)
}
