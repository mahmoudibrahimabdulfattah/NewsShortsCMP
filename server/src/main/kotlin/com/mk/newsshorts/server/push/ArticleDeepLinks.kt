package com.mk.newsshorts.server.push

import com.mk.newsshorts.server.model.FeedArticleDto
import java.net.URLEncoder

/**
 * Builds the `newsshorts://article` link a notification carries, so tapping it
 * opens the article inside the app rather than in a browser.
 *
 * The client parses the same shape; the two modules cannot share code, so the
 * format is pinned by a literal asserted in both test suites.
 */
object ArticleDeepLinks {

    const val SCHEME: String = "newsshorts"
    const val HOST: String = "article"

    /**
     * FCM rejects a data payload over 4096 bytes with a 400, which surfaces
     * only as a warning — the push would silently fail for every reader of that
     * language. Arabic costs about nine bytes per character once percent-
     * encoded, so the summary is trimmed until the whole link fits well inside
     * the budget shared with the title, body and url fields.
     */
    const val MAX_LINK_CHARS: Int = 2500

    fun build(article: FeedArticleDto): String {
        var summary = article.summary
        while (true) {
            val link = compose(article, summary)
            if (link.length <= MAX_LINK_CHARS || summary.isEmpty()) return link
            // Drop a proportional slice rather than one character at a time.
            val overflow = link.length - MAX_LINK_CHARS
            val trimTo = (summary.length - (overflow / 6).coerceAtLeast(1)).coerceAtLeast(0)
            summary = summary.take(trimTo)
        }
    }

    private fun compose(article: FeedArticleDto, summary: String): String = buildString {
        append(SCHEME).append("://").append(HOST)
        append("?url=").append(encode(article.url))
        append("&title=").append(encode(article.title))
        if (summary.isNotEmpty()) append("&summary=").append(encode(summary))
        article.imageUrl?.takeIf { it.isNotBlank() }?.let { append("&image=").append(encode(it)) }
        append("&source=").append(encode(article.sourceName))
        append("&category=").append(encode(article.category))
        append("&published=").append(article.publishedAt)
    }

    /**
     * URLEncoder emits form encoding, where a space becomes `+`. In a query
     * *value* an RFC 3986 decoder — which is what the client uses — reads that
     * as a literal plus, so spaces have to be re-encoded.
     */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
