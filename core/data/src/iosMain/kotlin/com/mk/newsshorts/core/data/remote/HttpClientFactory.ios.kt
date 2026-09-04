package com.mk.newsshorts.core.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(Darwin)
}

/** Kotlin/Native knows how the binary was built, so ask it rather than guess. */
@OptIn(ExperimentalNativeApi::class)
actual fun isDebugBuild(): Boolean = Platform.isDebugBinary
