package com.mk.newsshorts.core.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun createPlatformHttpClient(): HttpClient

/**
 * Whether this is a development build.
 *
 * Read from the build itself rather than from anything the device reports, so a
 * release build cannot be talked into behaving like a debug one.
 */
expect fun isDebugBuild(): Boolean

fun createHttpClient(): HttpClient {
    return createPlatformHttpClient().config {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                }
            )
        }
        // Debug only. LogLevel.BODY writes every response in full to logcat,
        // where anything else on the device with log access can read it. It is
        // useful while developing and has no place in a shipped build.
        if (isDebugBuild()) {
            install(Logging) {
                level = LogLevel.BODY
            }
        }
    }
}

