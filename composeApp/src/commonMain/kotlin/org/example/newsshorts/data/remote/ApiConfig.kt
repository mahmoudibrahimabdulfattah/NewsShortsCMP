package org.example.newsshorts.data.remote

import org.example.newsshorts.config.BuildConfig

expect fun isWebPlatform(): Boolean

/**
 * Adjusts the backend base URL for the current platform
 * (e.g. Android emulator reaches the host machine via 10.0.2.2).
 */
expect fun adjustBaseUrlForPlatform(url: String): String

object ApiConfig {
    /** News Shorts backend (own server) — configured via BACKEND_BASE_URL in local.properties. */
    val baseUrl: String = adjustBaseUrlForPlatform(BuildConfig.BACKEND_BASE_URL)

    fun feedUrl(): String = "$baseUrl/v1/feed"
}
