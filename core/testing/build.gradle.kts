plugins {
    id("newsshorts.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            api(projects.core.data)
            api(projects.core.model)
            api(libs.kotlin.test)
        }
    }
}
