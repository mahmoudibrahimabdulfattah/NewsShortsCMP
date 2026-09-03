package com.mk.newsshorts.core.model.article

import com.mk.newsshorts.core.model.NewsArticle

/**
 * The article being shown full-screen.
 *
 * Holds the article itself rather than an index: [ArticleId] is derived from
 * list position, and an article arriving from a notification is not in any list
 * at all on a cold start.
 */
data class ArticleDetails(
    val article: NewsArticle,
    val origin: ArticleOpenOrigin
)

/** Where a details screen was opened from - reported with the analytics event. */
enum class ArticleOpenOrigin(val analyticsValue: String) {
    FEED("feed"),
    SAVED("saved"),
    PUSH("push"),
    /** A shared link. Kept apart from PUSH so the two can be compared. */
    SHARE("share"),
    /** A search result. Says whether search finds people anything worth opening. */
    SEARCH("search")
}
