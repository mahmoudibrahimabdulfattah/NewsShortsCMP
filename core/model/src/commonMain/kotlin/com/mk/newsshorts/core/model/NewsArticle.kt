package com.mk.newsshorts.core.model

import kotlin.jvm.JvmInline

data class NewsArticle(
    val id: ArticleId,
    val title: ArticleTitle,
    val description: ArticleDescription,
    val content: ArticleContent,
    val author: ArticleAuthor,
    val source: NewsSource,
    val imageUrl: ImageUrl?,
    val articleUrl: ArticleUrl,
    val publishedAt: PublishedTimestamp,
    val category: NewsCategory
)

@JvmInline
value class PublishedTimestamp(val epochMillis: Long) {
    fun toReadableString(): String {
        val seconds: Long = epochMillis / 1000
        val minutes: Long = seconds / 60
        val hours: Long = minutes / 60
        val days: Long = hours / 24
        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    }
}

@JvmInline
value class ArticleId(val value: String)

@JvmInline
value class ArticleTitle(val value: String) {
    init {
        require(value.isNotBlank()) { "Article title cannot be blank" }
    }
}

@JvmInline
value class ArticleDescription(val value: String)

@JvmInline
value class ArticleContent(val value: String)

@JvmInline
value class ArticleAuthor(val value: String)

@JvmInline
value class ImageUrl(val value: String) {
    init {
        require(value.isNotBlank()) { "Image URL cannot be blank" }
    }
}

@JvmInline
value class ArticleUrl(val value: String) {
    init {
        require(value.isNotBlank()) { "Article URL cannot be blank" }
    }
}

data class NewsSource(
    val id: SourceId,
    val name: SourceName
)

@JvmInline
value class SourceId(val value: String)

@JvmInline
value class SourceName(val value: String)
