plugins {
    id("newsshorts.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Article imagery. The feed is the only module that loads remote
            // images; everything else draws vectors or text.
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
    }
}
