package com.mk.newsshorts.server.model

import kotlinx.serialization.Serializable

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

@Serializable
data class FeedResponse(
    val articles: List<FeedArticleDto>,
    val total: Long,
    /**
     * The file holding the next page of this feed, or null at the end of it.
     *
     * A bare file name rather than a URL: the client resolves it against the
     * feed directory it already knows, so a feed file can never point a reader
     * at another host. Null on a build that predates pagination, which reads
     * one file and stops.
     */
    val nextPage: String? = null,
)

data class FeedSource(
    val name: String,
    val url: String,
    val language: String,
    val category: String,
    /** ISO country code when the source covers one country's news (e.g. "eg"). */
    val country: String? = null,
)

data class RawArticle(
    val title: String,
    val url: String,
    val description: String?,
    val imageUrl: String?,
    val publishedAtMillis: Long,
    val source: FeedSource,
)
