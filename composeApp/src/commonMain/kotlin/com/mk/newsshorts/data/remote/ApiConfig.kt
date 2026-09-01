package com.mk.newsshorts.data.remote

import com.mk.newsshorts.config.BuildConfig

expect fun isWebPlatform(): Boolean

/** Makes a local backend reachable from each platform's development runtime. */
expect fun adjustBaseUrlForPlatform(url: String): String

class ApiConfig(
    configuredOrigins: List<String> = BuildConfig.BACKEND_ORIGINS.split(','),
) {
    val origins: List<String> = configuredOrigins
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { origin -> adjustBaseUrlForPlatform(origin.trimEnd('/')) }
        .distinct()
        .also { origins -> require(origins.isNotEmpty()) { "At least one backend origin is required" } }

    val primaryOrigin: String = origins.first()

    /**
     * The feed is static JSON, so every origin serves the same relative paths.
     * Keeping the origin out of the path lets one failed host be replaced
     * without changing what resource the caller asked for.
     */
    fun feedPath(language: String?, category: String?): String {
        val name = when {
            language == null -> DEFAULT_LANGUAGE
            category == null -> language
            else -> "$language-$category"
        }
        return "/v1/feed/$name.json"
    }

    fun countryFeedPath(countryCode: String, language: String): String =
        "/v1/feed/country-$countryCode-$language.json"

    /**
     * Anything other than a plain published file name ends the feed locally;
     * a downloaded page cannot redirect the app to another path or host.
     */
    fun feedPagePath(pageFile: String): String? {
        if (!PAGE_FILE.matches(pageFile)) return null
        return "/v1/feed/$pageFile"
    }

    fun searchIndexPath(language: String): String = "/v1/search/$language.json"

    fun notificationsPath(language: String): String = "/v1/notifications/$language.json"

    fun appConfigPath(): String = "/v1/app.json"

    fun url(origin: String, path: String): String =
        "${origin.trimEnd('/')}/${path.trimStart('/')}"

    private companion object {
        val PAGE_FILE = Regex("[A-Za-z0-9_-]+\\.json")
        const val DEFAULT_LANGUAGE: String = "en"
    }
}
