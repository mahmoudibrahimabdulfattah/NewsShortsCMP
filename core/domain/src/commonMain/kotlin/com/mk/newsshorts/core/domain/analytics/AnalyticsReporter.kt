package com.mk.newsshorts.core.domain.analytics

import com.mk.newsshorts.core.model.analytics.AnalyticsEvent

/**
 * Usage and crash reporting.
 *
 * Firebase only ships for Android and iOS, and the config file is optional, so
 * every call has to be safe when there is no backend behind it — the default
 * implementation no-ops rather than throw.
 */
interface AnalyticsReporter {
    fun logEvent(event: AnalyticsEvent)

    /** Attaches context to any crash reported afterwards. */
    fun setProperty(name: String, value: String)

    /** Records a handled failure without crashing the app. */
    fun recordError(message: String, cause: Throwable? = null)
}
