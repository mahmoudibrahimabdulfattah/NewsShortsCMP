package com.mk.newsshorts.core.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(OkHttp)
}


/** The build type the app shell recorded from its own BuildConfig — not from the device. */
actual fun isDebugBuild(): Boolean = com.mk.newsshorts.core.data.AndroidBuildType.isDebug
