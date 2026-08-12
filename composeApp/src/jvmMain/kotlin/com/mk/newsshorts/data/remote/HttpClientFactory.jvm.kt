package com.mk.newsshorts.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java

actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(Java)
}


// No shipped build of this target exists yet; treated as development.
actual fun isDebugBuild(): Boolean = true
