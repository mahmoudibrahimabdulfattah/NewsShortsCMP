import com.mk.newsshorts.buildlogic.configureAndroidLibrary
import com.mk.newsshorts.buildlogic.configureNewsshortsContractTargets

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val targetMode = providers.gradleProperty("newsshorts.contract.targets").orElse("all").get()
if (targetMode != "jvm") {
    apply(plugin = "com.android.library")
}

configureNewsshortsContractTargets(targetMode)
if (targetMode != "jvm") {
    configureAndroidLibrary()
}
