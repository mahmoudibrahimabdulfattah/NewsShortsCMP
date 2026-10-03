import com.mk.newsshorts.buildlogic.enableAndroidResources
import com.mk.newsshorts.buildlogic.configureNewsshortsComposeDependencies

plugins {
    id("newsshorts.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Compose resources ship as Android assets, which need Android resource processing on.
enableAndroidResources()
configureNewsshortsComposeDependencies()
