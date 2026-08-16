package com.mk.newsshorts.data.remote

import kotlinx.serialization.Serializable

/** Response shapes of the News Shorts backend (/v1/feed). */
@Serializable
data class BackendFeedResponse(
    val articles: List<BackendArticleDto>,
    val total: Long,
    /**
     * The file holding the next page, or null at the end of the feed. Defaulted
     * so the app keeps reading a backend that predates pagination.
     */
    val nextPage: String? = null,
)

@Serializable
data class BackendArticleDto(
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
