package com.mk.newsshorts.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.konan.target.HostManager

internal fun Project.configureNewsshortsKmpTargets() {
    val libs = libsCatalog()

    extensions.configure<KotlinMultiplatformExtension>("kotlin") {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }

        androidTarget {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }

        iosArm64()
        iosSimulatorArm64()
        jvm()
        js {
            browser()
            binaries.executable()
        }
        configureWasmJsTarget()

        sourceSets.named("commonTest") {
            dependencies {
                implementation(libs.requiredLibrary("kotlin-test"))
                implementation(libs.requiredLibrary("kotlinx-coroutinesTest"))
            }
        }
    }

    if (HostManager.hostIsMac) {
        tasks.named("check") {
            dependsOn("iosSimulatorArm64Test")
        }
    }
}

internal fun Project.configureAndroidLibrary() {
    val libs = libsCatalog()

    extensions.configure<LibraryExtension>("android") {
        namespace = "com.mk.newsshorts" + path.replace(':', '.')
        compileSdk = libs.requiredVersion("android-compileSdk").toInt()

        defaultConfig {
            minSdk = libs.requiredVersion("android-minSdk").toInt()
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}

internal fun Project.configureNewsshortsComposeDependencies() {
    val libs = libsCatalog()
    val compose = extensions.getByType<ComposeExtension>().dependencies

    extensions.configure<KotlinMultiplatformExtension>("kotlin") {
        sourceSets.named("commonMain") {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                // Arrives transitively through material3, but declared so a future
                // Compose bump cannot silently remove it.
                implementation(libs.requiredLibrary("compose-ui-backhandler"))
            }
        }
    }
}

internal fun Project.configureNewsshortsAppDependencies() {
    val libs = libsCatalog()

    extensions.configure<KotlinMultiplatformExtension>("kotlin") {
        sourceSets.named("commonMain") {
            dependencies {
                implementation(libs.requiredLibrary("kotlinx-coroutinesCore"))
                implementation(libs.requiredLibrary("kotlinx-serialization-json"))
                implementation(libs.requiredBundle("ktor-client-common"))
                implementation(libs.requiredBundle("koin-common"))
                implementation(libs.requiredLibrary("coil-compose"))
                implementation(libs.requiredLibrary("coil-network-ktor3"))
            }
        }
        sourceSets.named("commonTest") {
            dependencies {
                implementation(libs.requiredLibrary("ktor-client-mock"))
            }
        }
    }
}

private fun Project.libsCatalog(): VersionCatalog =
    extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun VersionCatalog.requiredLibrary(alias: String) =
    findLibrary(alias).orElseThrow { IllegalArgumentException("Missing library: $alias") }

private fun VersionCatalog.requiredBundle(alias: String) =
    findBundle(alias).orElseThrow { IllegalArgumentException("Missing bundle: $alias") }

private fun VersionCatalog.requiredVersion(alias: String): String =
    findVersion(alias).orElseThrow { IllegalArgumentException("Missing version: $alias") }.requiredVersion

@OptIn(ExperimentalWasmDsl::class)
private fun KotlinMultiplatformExtension.configureWasmJsTarget() {
    wasmJs {
        browser()
        binaries.executable()
    }
}
