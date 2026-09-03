import com.mk.newsshorts.buildlogic.configureAndroidLibrary
import com.mk.newsshorts.buildlogic.configureNewsshortsKmpTargets
import com.mk.newsshorts.buildlogic.registerPackageLayeringCheck

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

configureNewsshortsKmpTargets()
configureAndroidLibrary()
registerPackageLayeringCheck()
