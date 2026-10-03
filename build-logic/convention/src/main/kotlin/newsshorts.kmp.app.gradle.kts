import com.mk.newsshorts.buildlogic.configureAndroidLibrary
import com.mk.newsshorts.buildlogic.configureNewsshortsAppDependencies
import com.mk.newsshorts.buildlogic.configureNewsshortsComposeDependencies
import com.mk.newsshorts.buildlogic.configureNewsshortsKmpTargets
import com.mk.newsshorts.buildlogic.enableAndroidResources
import com.mk.newsshorts.buildlogic.registerPackageLayeringCheck

// The shared app code. AGP 9 no longer lets a multiplatform module be the Android application,
// so this is a library and the installable Android app is :androidApp.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

configureNewsshortsKmpTargets(produceExecutables = true)
configureAndroidLibrary(namespace = "com.mk.newsshorts")
enableAndroidResources()
configureNewsshortsComposeDependencies()
configureNewsshortsAppDependencies()
registerPackageLayeringCheck()
