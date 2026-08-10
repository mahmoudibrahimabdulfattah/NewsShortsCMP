package org.example.newsshorts.data.remote

import org.example.newsshorts.config.BuildConfig

expect fun isWebPlatform(): Boolean

/**
 * Adjusts the backend base URL for the current platform
 * (e.g. Android emulator reaches the host machine via 10.0.2.2).
 */
expect fun adjustBaseUrlForPlatform(url: String): String

object ApiConfig {
    /** News Shorts backend — configured via BACKEND_BASE_URL in local.properties. */
    val baseUrl: String = adjustBaseUrlForPlatform(BuildConfig.BACKEND_BASE_URL.trimEnd('/'))

    /**
     * The feed is published as static JSON (GitHub Pages), so language and
     * category are part of the path rather than query parameters. A local Ktor
     * server serves the same paths, so both work with one client.
     */
    fun feedUrl(language: String?, category: String?): String {
        val name = when {
            language == null -> DEFAULT_LANGUAGE
            category == null -> language
            else -> "$language-$category"
        }
        return "$baseUrl/v1/feed/$name.json"
    }

    fun countryFeedUrl(countryCode: String): String =
        "$baseUrl/v1/feed/country-$countryCode.json"

    private const val DEFAULT_LANGUAGE: String = "en"
}
