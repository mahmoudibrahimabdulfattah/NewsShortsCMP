plugins {
    id("newsshorts.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.contract)
            api(projects.core.model)
            api(projects.core.ui)
            api(libs.kotlinx.coroutinesCore)
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
