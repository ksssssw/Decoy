pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Peekaboo"
include(":app")
include(":peekaboo-core")
include(":peekaboo-android")
include(":peekaboo-okhttp")
include(":peekaboo-okhttp-noop")
include(":peekaboo-ktor")
include(":peekaboo-ktor-noop")
