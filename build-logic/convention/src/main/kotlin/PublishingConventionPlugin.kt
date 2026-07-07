import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Configures Maven Central publishing for a library module via the vanniktech
 * maven-publish plugin. Coordinates come from the root `gradle.properties`
 * (`GROUP` / `VERSION_NAME`, `artifactId` = the module name); credentials and the
 * GPG signing key are read from `~/.gradle/gradle.properties`, never the repo.
 */
class PublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.vanniktech.maven.publish")

        extensions.configure<MavenPublishBaseExtension> {
            // Central Portal is the default host; releases are staged for manual
            // review in the portal (not auto-released).
            publishToMavenCentral()
            signAllPublications()

            pom {
                name.set(project.name)
                // Read lazily: modules set `description = "..."` in their build
                // script body, which runs after this plugin is applied.
                description.set(
                    provider {
                        project.description
                            ?: "Decoy — Android network inspector & mocker SDK"
                    }
                )
                url.set("https://github.com/ksssssw/Decoy")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/ksssssw/Decoy/blob/main/LICENSE")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("ksssssw")
                        name.set("ksssssw")
                        url.set("https://github.com/ksssssw")
                    }
                }
                scm {
                    url.set("https://github.com/ksssssw/Decoy")
                    connection.set("scm:git:git://github.com/ksssssw/Decoy.git")
                    developerConnection.set("scm:git:ssh://git@github.com/ksssssw/Decoy.git")
                }
            }
        }
    }
}
