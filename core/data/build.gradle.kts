plugins {
    id("newsshorts.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            api(projects.core.model)
            api(projects.core.contract)
            api(projects.core.config)
            api(libs.kotlinx.coroutinesCore)
            api(libs.ktor.client.core)
            api(libs.ktor.client.contentNegotiation)
            api(libs.ktor.serialization.json)
            api(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(projects.core.testing)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.messaging)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
            implementation(libs.google.id)
            implementation(libs.kotlinx.coroutinesPlayServices)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.java)
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
    buildFeatures {
        // isDebugBuild() reads AGP's per-module DEBUG flag here. The app's
        // release build consumes the library's release variant, so this is
        // false in shipped builds and does not skip the device-integrity check.
        buildConfig = true
    }
}
