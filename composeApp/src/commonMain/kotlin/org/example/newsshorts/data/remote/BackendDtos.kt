package org.example.newsshorts.data.remote

import kotlinx.serialization.Serializable

/** Response shapes of the News Shorts backend (/v1/feed). */
@Serializable
data class BackendFeedResponse(
    val articles: List<BackendArticleDto>,
    val total: Long,
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
