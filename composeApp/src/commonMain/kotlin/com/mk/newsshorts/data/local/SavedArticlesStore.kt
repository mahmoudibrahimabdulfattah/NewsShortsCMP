package com.mk.newsshorts.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.mk.newsshorts.domain.model.ArticleAuthor
import com.mk.newsshorts.domain.model.ArticleContent
import com.mk.newsshorts.domain.model.ArticleDescription
import com.mk.newsshorts.domain.model.ArticleId
import com.mk.newsshorts.domain.model.ArticleTitle
import com.mk.newsshorts.domain.model.ArticleUrl
import com.mk.newsshorts.domain.model.ImageUrl
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.model.NewsSource
import com.mk.newsshorts.domain.model.PublishedTimestamp
import com.mk.newsshorts.domain.model.SourceId
import com.mk.newsshorts.domain.model.SourceName

/**
 * A saved article on disk.
 *
 * Deliberately its own shape rather than the feed's wire format: a bookmark has
 * to survive changes to the backend response, and it keeps its own category,
 * which the feed cache cannot (it stores one category per cached feed).
 */
@Serializable
private data class SavedArticleDto(
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String? = null,
    val sourceName: String = "",
    val category: String = "general",
    val publishedAt: Long = 0L,
)

/**
 * The disk half of bookmarking, behind an interface.
 *
 * The real implementation needs a [SettingsStorage], which is an `expect class`
 * with no actual in commonTest — so without this seam nothing that persists
 * bookmarks could be tested on any target.
 */
interface SavedArticlesLocalStore {
    fun load(): List<NewsArticle>
    fun save(articles: List<NewsArticle>)
}

/**
 * Bookmarks, persisted across launches.
 *
 * They previously lived only in memory, so the Saved Articles list emptied
 * itself every time the app was closed — the one place in the app where a user
 * deliberately keeps something.
 */
class SavedArticlesStore(
    private val settingsStorage: SettingsStorage
) : SavedArticlesLocalStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): List<NewsArticle> {
        val raw = settingsStorage.getString(KEY_SAVED_ARTICLES, "")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<SavedArticleDto>>(raw).mapNotNull { it.toDomain() }
        }.getOrElse {
            // A shape change or a truncated write should cost the bookmarks,
            // not every launch from here on.
            emptyList()
        }
    }

    override fun save(articles: List<NewsArticle>) {
        val payload = cappedForStorage(articles).map { it.toDto() }
        runCatching {
            settingsStorage.putString(KEY_SAVED_ARTICLES, json.encodeToString(payload))
        }
    }

    private fun NewsArticle.toDto(): SavedArticleDto = SavedArticleDto(
        title = title.value,
        summary = description.value,
        url = articleUrl.value,
        imageUrl = imageUrl?.value,
        sourceName = source.name.value,
        category = category.apiValue,
        publishedAt = publishedAt.epochMillis,
    )

    /** Null when the row can't make a valid article; the value classes reject blanks. */
    private fun SavedArticleDto.toDomain(): NewsArticle? = runCatching {
        NewsArticle(
            id = ArticleId("saved_${url.hashCode()}"),
            title = ArticleTitle(title),
            description = ArticleDescription(summary),
            content = ArticleContent(summary),
            author = ArticleAuthor(sourceName),
            source = NewsSource(
                id = SourceId(sourceName.lowercase().replace(" ", "-")),
                name = SourceName(sourceName),
            ),
            imageUrl = imageUrl?.takeIf { it.isNotBlank() }?.let { ImageUrl(it) },
            articleUrl = ArticleUrl(url),
            publishedAt = PublishedTimestamp(publishedAt),
            category = NewsCategory.fromApiValue(category),
        )
    }.getOrNull()

    private companion object {
        const val KEY_SAVED_ARTICLES: String = "saved_articles"
    }
}

/**
 * Applied on the way to disk rather than in the repository, so the cap is one
 * number in one place — and a top-level function so a test can check it without
 * a [SettingsStorage]. Same shape as `orderedWithCap` in SeenArticlesStore.
 */
internal fun cappedForStorage(articles: List<NewsArticle>): List<NewsArticle> =
    articles.take(MAX_SAVED)

/** Bounded so the bookmark list can't grow past what the store can hold. */
internal const val MAX_SAVED: Int = 200
