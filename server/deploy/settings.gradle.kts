// Standalone Gradle settings used only for container builds (Render, etc.).
// It includes just :server so the image never needs the Android SDK.
rootProject.name = "NewsShortsServer"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":server")
