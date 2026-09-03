plugins {
    id("newsshorts.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.contract)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.ktor.client.core) // Url powers ArticleDeepLinks parsing.
        }
    }
}
