// google() is a Maven repository, not the Android SDK. Compose Multiplatform
// publishes its androidx.{annotation,collection,lifecycle} dependencies there
// for every target, including iOS. This project still declares no Android
// target and applies no Android Gradle Plugin, so no SDK install is required.
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "my-odyssey"

include(":engine")
include(":composeApp")
