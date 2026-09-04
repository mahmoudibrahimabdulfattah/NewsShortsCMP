rootProject.name = "NewsShorts"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
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
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")
include(":core:config")
include(":core:contract")
include(":core:data")
include(":core:localization")
include(":core:model")
include(":core:navigation")
include(":core:ui")
include(":core:domain")
include(":core:testing")
include(":feature:auth")
include(":feature:feed")
include(":feature:inbox")
include(":feature:saved")
include(":feature:search")
include(":feature:settings")
include(":server")
