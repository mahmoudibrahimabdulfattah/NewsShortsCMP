package com.mk.newsshorts.server.model

enum class SourceMode {
    SUMMARY,
    HEADLINE,
    BLOCKED,
}

data class FeedSource(
    val name: String,
    val url: String,
    val language: String,
    val category: String,
    /** ISO country code when the source covers one country's news (e.g. "eg"). */
    val country: String? = null,
    /** Extra sections for a genuinely combined feed such as science + health. */
    val additionalCategories: Set<String> = emptySet(),
    /** Unknown source permissions must not inherit full processing rights. */
    val mode: SourceMode = SourceMode.BLOCKED,
    /** Permission to store and serve publisher-supplied images. */
    val allowsPublisherImages: Boolean = false,
    /** Source-wide licence identifier, when the whole feed is under one. */
    val licenseName: String? = null,
    /** Link to the source-wide licence terms. */
    val licenseUrl: String? = null,
) {
    val categories: Set<String>
        get() = linkedSetOf(category).apply { addAll(additionalCategories) }
}

data class SourceLicense(
    val name: String,
    val url: String?,
)

val FeedSource.license: SourceLicense?
    get() = licenseName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
        SourceLicense(
            name = name,
            url = licenseUrl?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

val FeedSource.isFetchable: Boolean
    get() = mode != SourceMode.BLOCKED

fun Iterable<FeedSource>.summarySourceNames(): Set<String> =
    filter { it.mode == SourceMode.SUMMARY }.mapTo(linkedSetOf()) { it.name }

fun Iterable<FeedSource>.publishableSourceNames(): Set<String> =
    filter { it.mode != SourceMode.BLOCKED }.mapTo(linkedSetOf()) { it.name }

fun Iterable<FeedSource>.licensesBySourceName(): Map<String, SourceLicense> =
    mapNotNull { source -> source.license?.let { source.name to it } }.toMap()

data class RawArticle(
    val title: String,
    val url: String,
    val description: String?,
    val imageUrl: String?,
    val publishedAtMillis: Long,
    val source: FeedSource,
    /** Article-level section evidence from RSS taxonomy or a clear URL path. */
    val candidateCategories: Set<String> = emptySet(),
    val publishedAtIsPublication: Boolean = true,
    val author: String? = null,
    val rightsNotice: String? = null,
)
