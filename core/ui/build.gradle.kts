plugins {
    id("newsshorts.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.localization)
            api(projects.core.model)
            api(libs.kotlinx.coroutinesCore)
            // ViewModel is multiplatform now, so BaseViewModel can be one
            // common class instead of six expect/actual ones — and the Koin
            // viewModel { } DSL, which requires an androidx ViewModel, works
            // on every target rather than only Android.
            api(libs.androidx.lifecycle.viewmodel)
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
