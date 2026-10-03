import com.mk.newsshorts.buildlogic.configureAndroidLibrary
import com.mk.newsshorts.buildlogic.configureNewsshortsKmpTargets
import com.mk.newsshorts.buildlogic.registerPackageLayeringCheck

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

configureNewsshortsKmpTargets()
configureAndroidLibrary()
registerPackageLayeringCheck()
