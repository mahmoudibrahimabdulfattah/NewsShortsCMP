package com.mk.newsshorts.core.model

/**
 * The languages the backend actually publishes.
 *
 * Stored or synced preferences from older builds can name other languages, so
 * anything else has to be mapped onto one before it reaches a feed URL or a
 * push topic.
 */
object FeedLanguage {
    const val DEFAULT: String = "en"

    private val supported: Set<String> = setOf("en", "ar")

    fun resolve(language: String?): String =
        if (language != null && language in supported) language else DEFAULT
}
