package com.mk.newsshorts.domain.model

/**
 * The languages the backend actually publishes.
 *
 * The settings screen offers more than these, so anything else has to be
 * mapped onto one before it reaches a feed URL or a push topic — otherwise the
 * app requests a file that was never generated, or subscribes to a topic
 * nothing is ever sent to.
 */
object FeedLanguage {
    const val DEFAULT: String = "en"

    private val supported: Set<String> = setOf("en", "ar")

    fun resolve(language: String?): String =
        if (language != null && language in supported) language else DEFAULT
}
