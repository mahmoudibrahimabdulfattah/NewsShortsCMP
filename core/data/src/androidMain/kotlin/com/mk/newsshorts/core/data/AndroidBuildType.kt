package com.mk.newsshorts.core.data

/**
 * The Android app's build type.
 *
 * Under AGP 9 a multiplatform library has a single Android variant and no `BuildConfig.DEBUG` of its
 * own, so the app shell (`:androidApp`) records its flag here before anything reads it. It comes from
 * the app's generated BuildConfig, never from the device, which an attacker controls. It starts as
 * release, so a build that forgot to set it skips nothing.
 */
object AndroidBuildType {
    @Volatile
    var isDebug: Boolean = false
}
