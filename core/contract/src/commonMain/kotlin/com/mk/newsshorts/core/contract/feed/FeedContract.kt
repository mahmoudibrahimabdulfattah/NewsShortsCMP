package com.mk.newsshorts.core.contract.feed

import kotlinx.serialization.Serializable

/** Response shapes of the News Shorts backend (/v1/feed). */
@Serializable
data class FeedResponse(
    val articles: List<FeedArticleDto>,
    val total: Long,
    /**
     * The file holding the next page of this feed, or null at the end of it.
     *
     * A bare file name rather than a URL: the client resolves it against the
     * feed directory it already knows, so a feed file can never point a reader
     * at another host. Defaulted so the app keeps reading a backend that
     * predates pagination.
     */
    val nextPage: String? = null,
)

@Serializable
data class FeedArticleDto(
    val id: Long,
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String?,
    val sourceName: String,
    val language: String,
    val category: String,
    val publishedAt: Long,
)
