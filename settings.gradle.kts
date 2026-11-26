// google() is Google's own Maven host, used only to fetch the Android Gradle plugin and platform libraries.
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
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

rootProject.name = "mumins-day"

include(":engine:calctimes")
include(":core")
include(":tools:content-gate")
include(":app")
