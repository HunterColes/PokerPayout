@file:Suppress("UnstableApiUsage")

include(":app")
// Baseline profiles disabled for F-Droid reproducible builds
// include(":baseline-profiles")
include(":tournament-feature")
include(":bank-feature")
include(":tools-feature")
include(":core")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

buildCache {
    local {
        isEnabled = true
        directory = File(rootDir, "build-cache")
    }
}
