package com.mk.newsshorts.domain.model

/**
 * One page of the feed, and how to reach the one below it.
 *
 * [nextPage] is the file the backend published the next page under, or null at
 * the end of the feed. A bare name rather than a URL — see the backend's
 * `FeedResponse.nextPage` — so the app resolves it against the feed directory
 * it already knows and a feed file can never point a reader at another host.
 */
data class FeedPage(
    val articles: List<NewsArticle>,
    val nextPage: String?,
)
