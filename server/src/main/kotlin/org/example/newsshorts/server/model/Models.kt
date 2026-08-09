package org.example.newsshorts.server.model

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
)

data class FeedSource(
    val name: String,
    val url: String,
    val language: String,
    val category: String,
)

data class RawArticle(
    val title: String,
    val url: String,
    val description: String?,
    val imageUrl: String?,
    val publishedAtMillis: Long,
    val source: FeedSource,
)
