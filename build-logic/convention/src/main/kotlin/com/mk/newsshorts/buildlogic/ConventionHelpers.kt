package com.mk.newsshorts.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
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

internal fun Project.configureNewsshortsContractTargets(targetMode: String) {
    val libs = libsCatalog()

    extensions.configure<KotlinMultiplatformExtension>("kotlin") {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }

        jvmToolchain(17)
        if (targetMode == "jvm") {
            jvm()
        } else {
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
            }
            wasmJs {
                browser()
            }
        }

        sourceSets.named("commonMain") {
            dependencies {
                implementation(libs.requiredLibrary("kotlinx-serialization-json"))
            }
        }
        sourceSets.named("commonTest") {
            dependencies {
                implementation(libs.requiredLibrary("kotlin-test"))
            }
        }
    }

    if (targetMode != "jvm" && HostManager.hostIsMac) {
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
                implementation(libs.requiredBundle("koin-common"))
                implementation(libs.requiredLibrary("coil-compose"))
                implementation(libs.requiredLibrary("coil-network-ktor3"))
            }
        }
    }
}

internal fun Project.registerPackageLayeringCheck() {
    val checkPackageLayering = tasks.register<PackageLayeringCheckTask>("checkPackageLayering") {
        group = "verification"
        description = "Fails when app package imports break the intended module layering."
        // Captured here rather than read from `project` in the task action:
        // getProject() throws on a configuration-cache hit, and this machine
        // never gets one, so the failure would first appear on someone else's.
        projectDirectory.set(layout.projectDirectory)
        source(project.layout.projectDirectory.asFileTree.matching {
            include("src/*Main/kotlin/**/*.kt")
            exclude("src/*Main/kotlin/**/BuildConfig.kt")
        })
    }
    tasks.named("check") {
        dependsOn(checkPackageLayering)
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

abstract class PackageLayeringCheckTask : SourceTask() {
    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    private val importRegex = Regex("""^\s*import\s+(com\.mk\.newsshorts(?:\.[A-Za-z_][A-Za-z0-9_]*)*)""")

    @TaskAction
    fun checkLayering() {
        val root = projectDirectory.get().asFile
        val violations = source.files
            .filter { it.isFile }
            .flatMap { file ->
                val sourcePackage = file.readLines().firstNotNullOfOrNull { line ->
                    line.removePrefix("package ").takeIf { it != line }
                } ?: return@flatMap emptyList()
                val sourceTier = tierFor(sourcePackage) ?: return@flatMap emptyList()
                file.readLines().mapIndexedNotNull { index, line ->
                    val imported = importRegex.find(line)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
                    val targetTier = tierFor(imported) ?: return@mapIndexedNotNull null
                    if (targetTier <= sourceTier || isAllowedBackEdge(sourcePackage, imported)) {
                        null
                    } else {
                        "${file.relativeTo(root)}:${index + 1}: $sourcePackage imports $imported"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                buildString {
                    appendLine("Package layering violations:")
                    violations.forEach { appendLine("  - $it") }
                },
            )
        }
    }

    private fun tierFor(packageName: String): Int? = when {
        packageName.startsWith("com.mk.newsshorts.core.contract") -> 0
        packageName.startsWith("com.mk.newsshorts.core.model") -> 1
        packageName.startsWith("com.mk.newsshorts.core.domain") -> 2
        packageName.startsWith("com.mk.newsshorts.core.data") -> 3
        packageName.startsWith("com.mk.newsshorts.auth") -> 3
        packageName.startsWith("com.mk.newsshorts.analytics") -> 3
        packageName.startsWith("com.mk.newsshorts.notifications") -> 3
        packageName.startsWith("com.mk.newsshorts.security") -> 3
        packageName.startsWith("com.mk.newsshorts.config") -> 3
        packageName.startsWith("com.mk.newsshorts.feature.") -> 4
        packageName.startsWith("com.mk.newsshorts.presentation.") -> 4
        packageName.startsWith("com.mk.newsshorts.navigation") -> 4
        packageName == "com.mk.newsshorts" || packageName.startsWith("com.mk.newsshorts.di") -> 5
        else -> null
    }

    private fun isAllowedBackEdge(sourcePackage: String, imported: String): Boolean {
        // Android delivers FCM through a system-instantiated service that must
        // remain in composeApp/androidMain; it posts into the app shell bus.
        if (
            sourcePackage == "com.mk.newsshorts.notifications" &&
            imported == "com.mk.newsshorts.navigation.NotificationBus"
        ) {
            return true
        }
        return false
    }
}

@OptIn(ExperimentalWasmDsl::class)
private fun KotlinMultiplatformExtension.configureWasmJsTarget() {
    wasmJs {
        browser()
        binaries.executable()
    }
}
