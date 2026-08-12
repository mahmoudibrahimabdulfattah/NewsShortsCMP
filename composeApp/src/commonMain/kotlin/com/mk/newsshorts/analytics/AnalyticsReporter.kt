package com.mk.newsshorts.analytics

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

/**
 * The events worth answering questions with. Kept as a sealed type so a typo
 * can't silently create a new event name that never shows up in a report.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, String> = emptyMap()) {

    /** A card was shown long enough to count as read rather than skipped. */
    class ArticleViewed(category: String, source: String, language: String) : AnalyticsEvent(
        name = "article_viewed",
        params = mapOf("category" to category, "source" to source, "language" to language),
    )

    /** Swiped past a card quickly — the signal that ranking is off. */
    class ArticleSkipped(category: String, source: String) : AnalyticsEvent(
        name = "article_skipped",
        params = mapOf("category" to category, "source" to source),
    )

    /** How deep a session goes; decides whether pagination is worth building. */
    class FeedDepthReached(depth: Int, category: String) : AnalyticsEvent(
        name = "feed_depth_reached",
        params = mapOf("depth" to depth.toString(), "category" to category),
    )

    /**
     * The reader opened the in-app details screen. [origin] separates a feed tap
     * from a saved-list tap from a notification.
     *
     * This replaces the old `article_opened`, which meant "the reader left for
     * the publisher". The click that fired it now does something much cheaper
     * and far more often, so reusing the name would have blended two behaviours
     * into one series.
     */
    class ArticleDetailsOpened(category: String, source: String, origin: String) : AnalyticsEvent(
        name = "article_details_opened",
        params = mapOf("category" to category, "source" to source, "origin" to origin),
    )

    /** The reader went on to the publisher's page — the expensive, rarer step. */
    class ArticleSourceOpened(category: String, source: String) : AnalyticsEvent(
        name = "article_source_opened",
        params = mapOf("category" to category, "source" to source),
    )

    /** Makes push-driven engagement measurable; previously it logged nothing. */
    class NotificationOpened(category: String, source: String) : AnalyticsEvent(
        name = "notification_opened",
        params = mapOf("category" to category, "source" to source),
    )

    class ArticleShared(category: String) : AnalyticsEvent(
        name = "article_shared",
        params = mapOf("category" to category),
    )

    class ArticleSaved(category: String) : AnalyticsEvent(
        name = "article_saved",
        params = mapOf("category" to category),
    )

    class CategorySelected(category: String) : AnalyticsEvent(
        name = "category_selected",
        params = mapOf("category" to category),
    )

    class CountrySelected(country: String) : AnalyticsEvent(
        name = "country_selected",
        params = mapOf("country" to country),
    )

    class NewsLanguageChanged(language: String) : AnalyticsEvent(
        name = "news_language_changed",
        params = mapOf("language" to language),
    )

    class AppLanguageChanged(language: String) : AnalyticsEvent(
        name = "app_language_changed",
        params = mapOf("language" to language),
    )

    /**
     * A build was blocked by the minimum supported version.
     *
     * Reported with the version that was blocked, so how many readers are stuck
     * on an old build — and whether they went on to update — is answerable
     * rather than guessed at.
     */
    class UpdateRequired(versionCode: Int) : AnalyticsEvent(
        name = "update_required",
        params = mapOf("version_code" to versionCode.toString()),
    )

    /** An empty or failed feed load, split by whether the cache covered it. */
    class FeedLoadFailed(reason: String, servedFromCache: Boolean) : AnalyticsEvent(
        name = "feed_load_failed",
        params = mapOf("reason" to reason, "served_from_cache" to servedFromCache.toString()),
    )
}

/** Discards everything. Used on targets without Firebase. */
object NoOpAnalyticsReporter : AnalyticsReporter {
    override fun logEvent(event: AnalyticsEvent) = Unit
    override fun setProperty(name: String, value: String) = Unit
    override fun recordError(message: String, cause: Throwable?) = Unit
}
