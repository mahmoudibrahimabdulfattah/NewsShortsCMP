import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

// google-services.json is not in version control, so the Firebase plugins are
// applied only when it is present. A clone without it still builds; reporting
// just stays off (see AnalyticsReporter).
val hasFirebaseConfig: Boolean = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
    apply(plugin = libs.plugins.firebaseCrashlytics.get().pluginId)
}

// Load backend base URL from local.properties and generate BuildConfig
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}
val backendBaseUrl: String = localProperties.getProperty("BACKEND_BASE_URL") ?: "http://localhost:8091"

// Shared links point at the published site, never at a local server: a link
// sent to someone else has to resolve on their device.
val shareBaseUrl: String = localProperties.getProperty("SHARE_BASE_URL")
    ?: "https://mahmoudibrahimabdulfattah.github.io/NewsShortsCMP"

// Generate BuildConfig.kt file
val buildConfigDir = file("src/commonMain/kotlin/org/example/newsshorts/config")
val buildConfigFile = file("src/commonMain/kotlin/org/example/newsshorts/config/BuildConfig.kt")

buildConfigDir.mkdirs()
buildConfigFile.writeText(
    """
    |package org.example.newsshorts.config
    |
    |object BuildConfig {
    |    const val BACKEND_BASE_URL: String = "$backendBaseUrl"
    |    const val SHARE_BASE_URL: String = "$shareBaseUrl"
    |}
    """.trimMargin()
)

kotlin {
    // Suppress expect/actual class warnings for all targets
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }
    
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser()
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            // Arrives transitively through material3, but declared so a future
            // Compose bump cannot silently remove it.
            implementation(libs.compose.ui.backhandler)
            // Kotlinx
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serialization.json)
            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            // Coil
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            // Lifecycle for Android
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.composeViewModel)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.browser)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            // Note: koin-compose-viewmodel has compatibility issues on iOS Native
            // Using direct Koin injection instead
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.java)
            // Lifecycle for Desktop
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.composeViewModel)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

android {
    namespace = "org.example.newsshorts"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // The identity Play and Firebase use, and the one baked into every
        // share link. It can never be changed once the app is published, so it
        // is deliberately not the org.example placeholder.
        // `namespace` stays on the Kotlin package so R and the manifest's
        // relative class names keep resolving; the two are allowed to differ.
        applicationId = "com.mk.newsshorts"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "org.example.newsshorts.MainKt"
        
        buildTypes.release.proguard {
            isEnabled = false
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "NewsShorts"
            packageVersion = "1.0.0"
            includeAllModules = true
            
            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icons/icon-512.png"))
                bundleID = "org.example.newsshorts"
            }
            
            windows {
                iconFile.set(project.file("src/jvmMain/resources/icons/icon-256.png"))
            }
            
            linux {
                iconFile.set(project.file("src/jvmMain/resources/icons/icon-512.png"))
            }
        }
    }
}
