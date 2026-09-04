plugins {
    id("newsshorts.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The privacy-policy URL shown on the Settings screen.
            implementation(projects.core.config)
        }
    }
}
