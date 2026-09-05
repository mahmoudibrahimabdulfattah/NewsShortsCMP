package com.mk.newsshorts.server.model

data class FeedSource(
    val name: String,
    val url: String,
    val language: String,
    val category: String,
    /** ISO country code when the source covers one country's news (e.g. "eg"). */
    val country: String? = null,
    /** Extra country tabs served by a source with broader regional coverage. */
    val additionalCountries: Set<String> = emptySet(),
    /** Extra sections for a genuinely combined feed such as science + health. */
    val additionalCategories: Set<String> = emptySet(),
    /** Drop feed items credited to third-party agencies before ingestion. */
    val excludeThirdPartyCredits: Boolean = false,
) {
    val countries: Set<String>
        get() = linkedSetOf<String>().apply {
            country?.let(::add)
            addAll(additionalCountries)
        }

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
