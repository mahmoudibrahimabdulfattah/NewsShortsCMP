package com.mk.newsshorts.core.domain.analytics

import com.mk.newsshorts.core.model.analytics.AnalyticsEvent

/**
 * Usage and crash reporting.
 *
 * Firebase only ships for Android and iOS, and the config file is optional, so
 * every call has to be safe when there is no backend behind it — the actual
 * implementations no-op rather than throw.
 */
interface AnalyticsReporter {
    fun logEvent(event: AnalyticsEvent)

    /** Attaches context to any crash reported afterwards. */
    fun setProperty(name: String, value: String)

    /** Records a handled failure without crashing the app. */
    fun recordError(message: String, cause: Throwable? = null)
}

/** Discards everything. Used on targets without Firebase. */
object NoOpAnalyticsReporter : AnalyticsReporter {
    override fun logEvent(event: AnalyticsEvent) = Unit
    override fun setProperty(name: String, value: String) = Unit
    override fun recordError(message: String, cause: Throwable?) = Unit
}
