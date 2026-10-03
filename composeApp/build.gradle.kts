import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("newsshorts.kmp.app")
    alias(libs.plugins.composeHotReload)
}

// The installable Android app (signing, Firebase plugins, deep-link hosts, BuildConfig) is :androidApp.

// gradle.properties is the one place the app version is declared.
val appVersionName = providers.gradleProperty("newsshorts.versionName")

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.composeViewModel)
            api(projects.core.config)
            api(projects.feature.auth)
            api(projects.feature.feed)
            api(projects.feature.inbox)
            api(projects.feature.saved)
            api(projects.feature.search)
            api(projects.feature.settings)
            api(projects.core.contract)
            api(projects.core.data)
            api(projects.core.model)
            api(projects.core.navigation)
            api(projects.core.localization)
            api(projects.core.ui)
            api(projects.core.domain)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            // Lifecycle for Android
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.composeViewModel)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.browser)
            implementation(libs.androidx.glance.appwidget)
        }
        iosMain.dependencies {
            // Note: koin-compose-viewmodel has compatibility issues on iOS Native
            // Using direct Koin injection instead
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            // Lifecycle for Desktop
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.composeViewModel)
        }
    }
}

compose.resources {
    generateResClass = never
}

compose.desktop {
    application {
        mainClass = "com.mk.newsshorts.MainKt"
        
        buildTypes.release.proguard {
            isEnabled = false
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "NewsShorts"
            packageVersion = appVersionName.get()
            includeAllModules = true
            
            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icons/icon-512.png"))
                bundleID = "com.mk.newsshorts"
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
