plugins {
    id("newsshorts.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.localization)
            api(projects.core.model)
            api(libs.kotlinx.coroutinesCore)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            api(libs.androidx.lifecycle.viewmodelCompose)
        }
        jvmMain.dependencies {
            api(libs.androidx.lifecycle.viewmodelCompose)
        }
    }
}

compose.resources {
    packageOfResClass = "com.mk.newsshorts.core.ui.resources"
}
