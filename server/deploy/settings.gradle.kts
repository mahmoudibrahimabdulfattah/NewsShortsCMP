// Standalone Gradle settings used only for container builds (Render, etc.).
// It includes :server and the JVM-only contract module so the image never
// needs the Android SDK.
rootProject.name = "NewsShortsServer"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        // This Android-free build still resolves the AGP jar because it is on
        // the convention plugin classpath. The contract plugin never applies
        // AGP in JVM-only mode, so the image still needs no Android SDK.
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":core:contract")
include(":server")
