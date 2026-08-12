package com.mk.newsshorts.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(Darwin)
}


// No shipped build of this target exists yet; treated as development.
actual fun isDebugBuild(): Boolean = true
