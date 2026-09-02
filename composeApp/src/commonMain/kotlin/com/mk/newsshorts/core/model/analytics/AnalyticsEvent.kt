package com.mk.newsshorts.core.model.analytics

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

    /**
     * A device-integrity check found something, whatever the policy then did
     * about it. Reported even when the policy is "allow", because the first
     * question worth answering is how many installs this would affect — that
     * number is what makes turning the policy up a decision rather than a
     * guess.
     */
    class DeviceIntegrityFailed(
        rooted: Boolean,
        debugger: Boolean,
        tampered: Boolean,
        emulator: Boolean,
        developerOptions: Boolean,
    ) : AnalyticsEvent(
        name = "device_integrity_failed",
        params = mapOf(
            "rooted" to rooted.toString(),
            "debugger" to debugger.toString(),
            "tampered" to tampered.toString(),
            "emulator" to emulator.toString(),
            "developer_options" to developerOptions.toString(),
        ),
    )

    /**
     * A search ran and settled.
     *
     * **The query text is not a parameter here and must never become one.** The
     * privacy policy tells readers that analytics carry a story's category,
     * publisher and language — that "article addresses and headlines are never
     * sent" — and a search query is more revealing than either. What is
     * reported is the shape of the search, not its subject: how many results
     * came back (a zero rate is the number that says whether the corpus is deep
     * enough and the Arabic folding is working), how long the query was, and
     * which corpus was searched.
     *
     * [queryLength] is a count of characters, not a sample of them.
     */
    class SearchPerformed(resultCount: Int, queryLength: Int, language: String) : AnalyticsEvent(
        name = "search_performed",
        params = mapOf(
            "result_count" to resultCount.toString(),
            "query_length" to queryLength.toString(),
            "language" to language,
        ),
    )

    /** An empty or failed feed load, split by whether the cache covered it. */
    class FeedLoadFailed(reason: String, servedFromCache: Boolean) : AnalyticsEvent(
        name = "feed_load_failed",
        params = mapOf("reason" to reason, "served_from_cache" to servedFromCache.toString()),
    )
}
