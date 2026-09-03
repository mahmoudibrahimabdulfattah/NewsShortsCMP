package com.mk.newsshorts.core.contract.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfigDto(
    val minSupportedVersionCode: Int = 1,
    val latestVersionCode: Int = 1,
    val storeUrl: String = "",
    /** "allow", "warn" or "block", matching the app's integrity policy values. */
    val rootPolicy: String = "warn",
    /**
     * Same values, for emulators and phones with developer options on. Defaults
     * to blocking, which is what a shipped build should do with a copy of
     * itself running in an emulator.
     */
    val emulatorPolicy: String = "block",
)
