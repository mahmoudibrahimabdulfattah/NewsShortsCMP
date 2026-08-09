package org.example.newsshorts.server.ingest

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.newsshorts.server.model.FeedSource
import org.example.newsshorts.server.model.RawArticle
import org.jdom2.Element
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI

class RssFetcher {

    private val log = LoggerFactory.getLogger(RssFetcher::class.java)

    suspend fun fetch(source: FeedSource): List<RawArticle> = withContext(Dispatchers.IO) {
        try {
            val connection = (URI(source.url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "NewsShortsBot/1.0 (+https://newsshorts.app)")
                setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
                instanceFollowRedirects = true
            }
            val feed = connection.inputStream.use { SyndFeedInput().build(XmlReader(it)) }
            feed.entries.mapNotNull { entry -> entry.toRawArticle(source) }
        } catch (e: Exception) {
            log.warn("Fetch failed for ${source.name}: ${e.message}")
            emptyList()
        }
    }

    private fun SyndEntry.toRawArticle(source: FeedSource): RawArticle? {
        val link = link?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val title = title?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val descriptionHtml = description?.value
        return RawArticle(
            title = title,
            url = link,
            description = descriptionHtml?.stripHtml()?.take(2000),
            imageUrl = extractImage(descriptionHtml),
            publishedAtMillis = (publishedDate ?: updatedDate)?.time ?: System.currentTimeMillis(),
            source = source,
        )
    }

    private fun SyndEntry.extractImage(descriptionHtml: String?): String? {
        enclosures.firstOrNull { it.type?.startsWith("image") == true }?.url?.let { return it }
        // media:content / media:thumbnail live in foreign markup
        foreignMarkup.firstOrNull { it.isMediaImage() }?.getAttributeValue("url")?.let { return it }
        return descriptionHtml?.let { IMG_SRC_REGEX.find(it)?.groupValues?.get(1) }
    }

    private fun Element.isMediaImage(): Boolean =
        namespacePrefix == "media" && (name == "thumbnail" || name == "content")

    private fun String.stripHtml(): String =
        replace(TAG_REGEX, " ").replace(WHITESPACE_REGEX, " ").trim()

    companion object {
        private val IMG_SRC_REGEX = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val TAG_REGEX = Regex("<[^>]*>")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
