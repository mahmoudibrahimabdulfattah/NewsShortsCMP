package com.mk.newsshorts.core.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(Js)
}


// No shipped build of this target exists yet; treated as development.
actual fun isDebugBuild(): Boolean = true
