plugins {
    id("newsshorts.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.kotlinx.coroutinesCore)
        }
    }
}
