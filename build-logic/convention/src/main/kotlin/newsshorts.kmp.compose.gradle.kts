import com.mk.newsshorts.buildlogic.configureNewsshortsComposeDependencies

plugins {
    id("newsshorts.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

configureNewsshortsComposeDependencies()
