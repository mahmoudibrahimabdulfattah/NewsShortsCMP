package com.mk.newsshorts.server.push

import com.mk.newsshorts.server.model.FeedArticleDto
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

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

    /**
     * Returns the article URL carried by one of this app's deep links.
     *
     * This deliberately decodes percent escapes only. Treating `+` as form
     * encoding would change a valid article URL and let the same story miss an
     * exact-URL de-duplication check.
     */
    fun articleUrlOf(link: String): String? {
        val uri = runCatching { URI(link) }.getOrNull() ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true) ||
            !uri.rawAuthority.equals(HOST, ignoreCase = true) ||
            !uri.rawPath.isNullOrEmpty() ||
            uri.rawFragment != null
        ) {
            return null
        }

        val encodedUrl = uri.rawQuery
            ?.split('&')
            ?.firstNotNullOfOrNull { entry ->
                val separator = entry.indexOf('=')
                if (separator >= 0 && entry.substring(0, separator) == "url") {
                    entry.substring(separator + 1)
                } else {
                    null
                }
            }
            ?: return null

        return percentDecode(encodedUrl)?.takeIf { it.isNotEmpty() }
    }

    private fun compose(article: FeedArticleDto, summary: String): String = link(
        url = article.url,
        title = article.title,
        summary = summary,
        imageUrl = article.imageUrl,
        sourceName = article.sourceName,
        category = article.category,
        publishedAt = article.publishedAt,
    )

    /**
     * The same link from loose fields, for callers that do not hold a
     * [FeedArticleDto] — the share page builds one out of its archive row.
     *
     * Public so the format lives in exactly one place: a second builder that
     * agreed with this one today would drift the first time either changed, and
     * the client parses both.
     *
     * [referrer] becomes `src`, which is how the app tells a share-driven open
     * apart from a notification tap.
     */
    fun link(
        url: String,
        title: String,
        summary: String,
        imageUrl: String?,
        sourceName: String,
        category: String,
        publishedAt: Long,
        referrer: String? = null,
    ): String = buildString {
        append(SCHEME).append("://").append(HOST)
        append("?url=").append(encode(url))
        append("&title=").append(encode(title))
        if (summary.isNotEmpty()) append("&summary=").append(encode(summary))
        imageUrl?.takeIf { it.isNotBlank() }?.let { append("&image=").append(encode(it)) }
        append("&source=").append(encode(sourceName))
        append("&category=").append(encode(category))
        append("&published=").append(publishedAt)
        referrer?.takeIf { it.isNotBlank() }?.let { append("&src=").append(encode(it)) }
    }

    /**
     * URLEncoder emits form encoding, where a space becomes `+`. In a query
     * *value* an RFC 3986 decoder — which is what the client uses — reads that
     * as a literal plus, so spaces have to be re-encoded.
     */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun percentDecode(value: String): String? {
        val bytes = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                if (index + 2 >= value.length) return null
                val high = value[index + 1].digitToIntOrNull(16) ?: return null
                val low = value[index + 2].digitToIntOrNull(16) ?: return null
                bytes.write((high shl 4) or low)
                index += 3
            } else {
                val codePoint = value.codePointAt(index)
                val raw = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
                bytes.write(raw)
                index += Character.charCount(codePoint)
            }
        }

        return runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()))
                .toString()
        }.getOrNull()
    }
}
