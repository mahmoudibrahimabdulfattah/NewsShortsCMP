package org.example.newsshorts.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(Js)
}

