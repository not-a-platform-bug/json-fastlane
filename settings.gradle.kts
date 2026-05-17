pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "json-fastlane"

include(
    "json-fastlane-core",
    "json-fastlane-spring",
    "json-fastlane-netty",
    "json-fastlane-benchmarks"
)
