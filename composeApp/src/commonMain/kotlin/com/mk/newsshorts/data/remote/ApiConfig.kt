package com.mk.newsshorts.data.remote

import com.mk.newsshorts.config.BuildConfig

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

    fun countryFeedUrl(countryCode: String, language: String): String =
        "$baseUrl/v1/feed/country-$countryCode-$language.json"

    /**
     * A later page of a feed, named by the page before it.
     *
     * The name comes out of a downloaded file, so it is checked rather than
     * pasted into a URL: a plain file name, no path of its own. Anything else
     * returns null and the feed simply ends there — a feed file has no business
     * sending the app to a directory, let alone another host.
     */
    fun feedPageUrl(pageFile: String): String? {
        if (!PAGE_FILE.matches(pageFile)) return null
        return "$baseUrl/v1/feed/$pageFile"
    }

    private val PAGE_FILE = Regex("[A-Za-z0-9_-]+\\.json")

    /**
     * Everything published in one language, as one file.
     *
     * A static host cannot answer `?q=`, so there is no search endpoint to call
     * — the corpus comes down whole and the app matches against it. [language]
     * has already been narrowed to one the backend publishes, the same as every
     * feed URL above, or this would request a file that was never generated.
     */
    fun searchIndexUrl(language: String): String = "$baseUrl/v1/search/$language.json"

    /** What has been pushed in one language, for the in-app inbox. */
    fun notificationsUrl(language: String): String = "$baseUrl/v1/notifications/$language.json"

    /** Minimum supported build and store link — see [AppUpdateClient]. */
    fun appConfigUrl(): String = "$baseUrl/v1/app.json"

    private const val DEFAULT_LANGUAGE: String = "en"
}
