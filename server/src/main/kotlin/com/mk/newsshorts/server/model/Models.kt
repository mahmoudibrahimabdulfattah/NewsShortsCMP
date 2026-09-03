package com.mk.newsshorts.server.model

data class FeedSource(
    val name: String,
    val url: String,
    val language: String,
    val category: String,
    /** ISO country code when the source covers one country's news (e.g. "eg"). */
    val country: String? = null,
    /** Extra sections for a genuinely combined feed such as science + health. */
    val additionalCategories: Set<String> = emptySet(),
) {
    val categories: Set<String>
        get() = linkedSetOf(category).apply { addAll(additionalCategories) }
}

data class RawArticle(
    val title: String,
    val url: String,
    val description: String?,
    val imageUrl: String?,
    val publishedAtMillis: Long,
    val source: FeedSource,
    /** Article-level section evidence from RSS taxonomy or a clear URL path. */
    val candidateCategories: Set<String> = emptySet(),
)
