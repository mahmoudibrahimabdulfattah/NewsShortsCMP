package org.example.newsshorts.navigation

import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
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
    /** `share` when the landing page handed this over, absent for a push. */
    val referrer: String? = null,
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

    /** Value of `src` that the landing page adds when it hands off to the app. */
    const val SHARE_REFERRER: String = "share"

    private const val MAX_URL = 2000
    private const val MAX_TITLE = 300
    private const val MAX_SUMMARY = 4000

    /** Short enough to survive a chat bubble without being truncated. */
    private const val MAX_SHARE_URL = 1200

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
            referrer = parameters["src"].clean(MAX_TITLE),
        )
    }

    /** Trims, drops blanks, and caps length so an oversized field cannot stall layout. */
    private fun String?.clean(maxLength: Int): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }?.take(maxLength)

    private fun String.isWebUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    /**
     * The link put into a share sheet.
     *
     * Deliberately https and not `newsshorts://`: a custom scheme is not
     * clickable in most messaging apps and does nothing at all for a recipient
     * who hasn't installed the app. This points at a page on the published site
     * that hands off to the app when it is present and offers the store when it
     * is not — and it carries the same query as [parse] reads, so once the app
     * is on Play and the domain is verified, the same link can open the app
     * directly with no page in between.
     */
    fun shareUrl(article: NewsArticle, baseUrl: String, language: String): String {
        // Trimmed the same way the server trims its push link. Percent-encoded
        // Arabic runs about nine bytes a character, so an untrimmed summary
        // produces a link long enough for chat apps to cut off mid-URL.
        var summary = article.description.value.trim()
        while (true) {
            val candidate = compose(article, baseUrl, language, summary)
            if (candidate.length <= MAX_SHARE_URL || summary.isEmpty()) return candidate
            summary = summary.take((summary.length * 3) / 4).trimEnd()
        }
    }

    private fun compose(
        article: NewsArticle,
        baseUrl: String,
        language: String,
        summary: String,
    ): String = buildString {
        append(baseUrl.trimEnd('/')).append("/a/?")
        append("url=").append(article.articleUrl.value.encodeURLParameter())
        append("&title=").append(article.title.value.encodeURLParameter())
        summary.takeIf { it.isNotBlank() }
            ?.let { append("&summary=").append(it.encodeURLParameter()) }
        article.imageUrl?.value?.let { append("&image=").append(it.encodeURLParameter()) }
        append("&source=").append(article.source.name.value.encodeURLParameter())
        append("&category=").append(article.category.apiValue.encodeURLParameter())
        append("&published=").append(article.publishedAt.epochMillis)
        append("&lang=").append(language.encodeURLParameter())
    }
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
