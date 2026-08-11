package org.example.newsshorts.navigation

import io.ktor.http.Url
import org.example.newsshorts.domain.model.ArticleAuthor
import org.example.newsshorts.domain.model.ArticleContent
import org.example.newsshorts.domain.model.ArticleDescription
import org.example.newsshorts.domain.model.ArticleId
import org.example.newsshorts.domain.model.ArticleTitle
import org.example.newsshorts.domain.model.ArticleUrl
import org.example.newsshorts.domain.model.ImageUrl
import org.example.newsshorts.domain.model.NewsArticle
import org.example.newsshorts.domain.model.NewsCategory
import org.example.newsshorts.domain.model.NewsSource
import org.example.newsshorts.domain.model.PublishedTimestamp
import org.example.newsshorts.domain.model.SourceId
import org.example.newsshorts.domain.model.SourceName

/** An article carried by a notification tap or a `newsshorts://` link. */
data class ArticleDeepLink(
    val url: String,
    val title: String?,
    val summary: String?,
    val imageUrl: String?,
    val sourceName: String?,
    val category: String?,
    val publishedAtMillis: Long?,
)

/**
 * Parses and builds `newsshorts://article` links.
 *
 * The activity that receives these is exported, so any installed app can send
 * one: every field here is untrusted input. [parse] returns null rather than
 * throwing, because a malformed link arrives during launch and must not be able
 * to crash the app.
 */
object ArticleDeepLinks {

    const val SCHEME: String = "newsshorts"
    const val HOST: String = "article"

    private const val MAX_URL = 2000
    private const val MAX_TITLE = 300
    private const val MAX_SUMMARY = 4000

    fun parse(raw: String?): ArticleDeepLink? {
        if (raw.isNullOrBlank()) return null
        val url = runCatching { Url(raw) }.getOrNull() ?: return null
        if (!url.protocol.name.equals(SCHEME, ignoreCase = true)) return null
        if (!url.host.equals(HOST, ignoreCase = true)) return null

        val parameters = url.parameters
        // Without this the link could hand javascript:, file:, intent: or
        // content: URIs straight to the browser opener.
        val articleUrl = parameters["url"].clean(MAX_URL)?.takeIf { it.isWebUrl() } ?: return null

        return ArticleDeepLink(
            url = articleUrl,
            title = parameters["title"].clean(MAX_TITLE),
            summary = parameters["summary"].clean(MAX_SUMMARY),
            imageUrl = parameters["image"].clean(MAX_URL)?.takeIf { it.isWebUrl() },
            sourceName = parameters["source"].clean(MAX_TITLE),
            category = parameters["category"].clean(MAX_TITLE),
            publishedAtMillis = parameters["published"]?.toLongOrNull()?.takeIf { it > 0 },
        )
    }

    /** Trims, drops blanks, and caps length so an oversized field cannot stall layout. */
    private fun String?.clean(maxLength: Int): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }?.take(maxLength)

    private fun String.isWebUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
}

/**
 * Builds a displayable article from a link.
 *
 * Returns null when the title is missing: the value classes reject blank input,
 * and a details screen with no headline would be useless anyway.
 */
fun ArticleDeepLink.toNewsArticle(): NewsArticle? {
    val headline = title?.takeIf { it.isNotBlank() } ?: return null
    val source = sourceName.orEmpty()
    return runCatching {
        NewsArticle(
            id = ArticleId("push_${url.hashCode()}"),
            title = ArticleTitle(headline),
            description = ArticleDescription(summary.orEmpty()),
            // Mirrors NewsApiClient: the app never holds more than the summary.
            content = ArticleContent(summary.orEmpty()),
            author = ArticleAuthor(source),
            source = NewsSource(
                id = SourceId(source.lowercase().replace(" ", "-")),
                name = SourceName(source),
            ),
            imageUrl = imageUrl?.takeIf { it.isNotBlank() }?.let { ImageUrl(it) },
            articleUrl = ArticleUrl(url),
            publishedAt = PublishedTimestamp(publishedAtMillis ?: 0L),
            category = NewsCategory.fromApiValue(category.orEmpty()),
        )
    }.getOrNull()
}
