// Remove ANDROID_PREFS_ROOT if it was injected by older Android Studio versions.
// The reflection approach that modifies ProcessEnvironment no longer works on JVM 17+.
// We rely on Gradle's own environment handling instead.

pluginManagement {
    repositories {
        google()
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

rootProject.name = "AGAPE"
include(":app")
project(":app").projectDir = file("android/app")
