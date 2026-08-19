package com.mk.newsshorts.navigation

import io.ktor.http.Url
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

    /** Last path segment of a shared link, and of the published landing page. */
    const val LANDING_PATH: String = "a"

    private const val MAX_URL = 2000
    private const val MAX_TITLE = 300
    private const val MAX_SUMMARY = 4000

    fun parse(raw: String?): ArticleDeepLink? {
        if (raw.isNullOrBlank()) return null
        val url = runCatching { Url(raw) }.getOrNull() ?: return null
        if (!url.isArticleLink()) return null

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

    /**
     * Accepts both forms of the same link: the private `newsshorts://article`
     * scheme a notification and the landing page use, and the public
     * `https://…/a/` page that builds released before per-article pages existed
     * still share.
     *
     * That https form carries the whole article in its query string, which is
     * what makes it parseable here — and also why it never produced a preview
     * card in a chat. The link [shareUrl] builds now names a page instead, and
     * has nothing in it to parse; the page hands the article over through the
     * `newsshorts://` form on a tap. So the exact path is matched, not a prefix:
     * a link this parser cannot turn into an article must reach the browser
     * rather than open the app on the feed.
     */
    private fun Url.isArticleLink(): Boolean = when {
        protocol.name.equals(SCHEME, ignoreCase = true) -> host.equals(HOST, ignoreCase = true)
        protocol.name.equals("https", ignoreCase = true) -> encodedPath.trimEnd('/').endsWith("/$LANDING_PATH")
        else -> false
    }

    /**
     * Recognises a per-article landing page, and returns its URL unchanged.
     *
     * These carry no query string — they name a story rather than describing
     * one — so [parse] cannot read them and something has to fetch the page to
     * get the article back. This only decides whether that is worth doing.
     *
     * [baseUrl] is checked and not merely assumed. The activity receiving these
     * is exported, so any installed app can hand it a URL, and whatever recovers
     * an article from this one will go and fetch it: without the check, another
     * app could aim that fetch at a host of its choosing.
     */
    fun sharePageUrl(raw: String?, baseUrl: String): String? {
        if (raw.isNullOrBlank()) return null
        val url = runCatching { Url(raw) }.getOrNull() ?: return null
        if (!url.protocol.name.equals("https", ignoreCase = true)) return null
        val base = runCatching { Url(baseUrl) }.getOrNull() ?: return null
        if (!url.host.equals(base.host, ignoreCase = true)) return null

        val prefix = base.encodedPath.trimEnd('/') + "/" + LANDING_PATH + "/"
        if (!url.encodedPath.startsWith(prefix)) return null
        // Exactly a language and a slug after it. Anything else under the same
        // prefix is the stylesheet, the script, or the legacy landing page.
        val rest = url.encodedPath.removePrefix(prefix).trim('/').split('/')
        if (rest.size != 2 || rest.any { it.isEmpty() }) return null
        return raw
    }

    /** Trims, drops blanks, and caps length so an oversized field cannot stall layout. */
    private fun String?.clean(maxLength: Int): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }?.take(maxLength)

    private fun String.isWebUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    /**
     * The link put into a share sheet.
     *
     * Names a page on the published site rather than carrying the article in a
     * query string. The query-string form worked — the page drew the story on
     * load — but WhatsApp, Telegram and the rest fetch a shared URL and read its
     * `<head>` without running any script, so every shared story arrived as a
     * bare grey link with no headline, image or summary. The site now publishes
     * one page per article with those tags already in the markup, and this names
     * it. See the server's `SharePage`.
     *
     * Deliberately https and not `newsshorts://`: a custom scheme is not
     * clickable in most messaging apps and does nothing at all for a recipient
     * who has not installed the app. The page offers the store to them, and
     * hands the article to the app for anyone who has it.
     *
     * The language belongs in the path because the same article is a different
     * page in each one — a story shared out of the English feed should not open
     * in Arabic.
     */
    fun shareUrl(article: NewsArticle, baseUrl: String, language: String): String {
        val slug = ShareSlug.of(article.articleUrl.value)
        return "${baseUrl.trimEnd('/')}/$LANDING_PATH/${language.lowercase()}/$slug/"
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
