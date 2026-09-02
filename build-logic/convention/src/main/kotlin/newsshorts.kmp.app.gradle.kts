import com.mk.newsshorts.buildlogic.configureNewsshortsAppDependencies
import com.mk.newsshorts.buildlogic.configureNewsshortsComposeDependencies
import com.mk.newsshorts.buildlogic.configureNewsshortsKmpTargets
import com.mk.newsshorts.buildlogic.registerPackageLayeringCheck

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

configureNewsshortsKmpTargets()
configureNewsshortsComposeDependencies()
configureNewsshortsAppDependencies()
registerPackageLayeringCheck()
