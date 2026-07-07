import com.android.build.api.dsl.LibraryExtension
import com.decoy.buildlogic.addUnitTestDependencies
import com.decoy.buildlogic.intVersion
import com.decoy.buildlogic.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            compileSdk = libs.intVersion("projectCompileSdk")
            defaultConfig.minSdk = libs.intVersion("projectMinSdk")
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            explicitApi()
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
        addUnitTestDependencies()
    }
}
