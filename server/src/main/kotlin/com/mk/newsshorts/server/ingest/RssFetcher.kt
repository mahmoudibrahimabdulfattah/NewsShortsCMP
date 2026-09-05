package com.mk.newsshorts.server.ingest

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mk.newsshorts.server.model.FeedSource
import com.mk.newsshorts.server.model.RawArticle
import org.jdom2.Element
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

data class SourceSnapshot(
    val source: FeedSource,
    val articles: List<RawArticle>,
    val effectiveUrl: String,
    val thirdPartyCreditsDropped: Int = 0,
)

fun interface FeedFetcher {
    suspend fun fetch(source: FeedSource): SourceSnapshot
}

/**
 * Extracts source-side section evidence without reading the headline or body.
 * RSS category labels are explicit taxonomy. URL evidence is deliberately
 * stricter: only a complete path segment counts, so a slug such as
 * `sports-car-sales` cannot turn a business story into sport.
 */
internal fun inferArticleCategories(labels: Iterable<String>, articleUrl: String): Set<String> =
    buildSet {
        labels.forEach { label ->
            LABEL_TOKEN.split(label.lowercase()).filter(String::isNotBlank).forEach { token ->
                addAll(CATEGORY_ALIASES[token].orEmpty())
            }
        }
        try {
            URI(articleUrl).path.orEmpty()
                .split('/')
                .map { it.trim().lowercase() }
                .filter(String::isNotEmpty)
                .forEach { segment -> addAll(CATEGORY_ALIASES[segment].orEmpty()) }
        } catch (_: Exception) {
            // A malformed article URL is rejected later by normal feed use; it
            // simply provides no category evidence here.
        }
    }

class RssFetcher : FeedFetcher {

    private val log = LoggerFactory.getLogger(RssFetcher::class.java)

    override suspend fun fetch(source: FeedSource): SourceSnapshot = withContext(Dispatchers.IO) {
        try {
            val opened = open(source.url)
            val feed = opened.stream.use { SyndFeedInput().build(XmlReader(it)) }
            var thirdPartyCreditsDropped = 0
            val articles = feed.entries.mapNotNull { entry ->
                val descriptionHtml = entry.descriptionHtml()
                val descriptionText = descriptionHtml?.stripHtml()
                if (shouldExcludeForThirdPartyCredits(source, entry.title.orEmpty(), descriptionText)) {
                    thirdPartyCreditsDropped++
                    null
                } else {
                    entry.toRawArticle(source, descriptionHtml)
                }
            }
            SourceSnapshot(
                source = source,
                articles = articles,
                effectiveUrl = opened.effectiveUrl,
                thirdPartyCreditsDropped = thirdPartyCreditsDropped,
            )
        } catch (e: Exception) {
            log.warn("Fetch failed for ${source.name}: ${e.message}")
            SourceSnapshot(source, emptyList(), source.url)
        }
    }

    /**
     * HttpURLConnection refuses to follow redirects that switch protocol, which
     * silently empties feeds that redirect http -> https, so follow them here.
     */
    private data class OpenedFeed(val stream: InputStream, val effectiveUrl: String)

    private fun open(url: String, hop: Int = 0): OpenedFeed {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "NewsShortsBot/1.0 (+https://newsshorts.app)")
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
            instanceFollowRedirects = true
        }
        val location = connection.getHeaderField("Location")
        if (connection.responseCode in 300..399 && location != null && hop < MAX_REDIRECTS) {
            connection.disconnect()
            return open(URI(url).resolve(location).toString(), hop + 1)
        }
        return OpenedFeed(connection.inputStream, connection.url.toString())
    }

    private fun SyndEntry.toRawArticle(source: FeedSource, descriptionHtml: String?): RawArticle? {
        val link = link?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val title = title?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val descriptionText = descriptionHtml?.stripHtml()
        return RawArticle(
            title = title,
            url = link,
            description = descriptionText?.take(2000),
            imageUrl = extractImage(descriptionHtml),
            publishedAtMillis = (publishedDate ?: updatedDate)?.time ?: System.currentTimeMillis(),
            source = source,
            candidateCategories = inferArticleCategories(categories.map { it.name }, link),
        )
    }

    private fun SyndEntry.descriptionHtml(): String? {
        // Some feeds leave <description> empty and put the article body in
        // <content:encoded> instead; without it those entries reach the
        // summarizer with nothing but a headline.
        return description?.value?.takeUnless { it.isBlank() }
            ?: contents.firstOrNull { !it.value.isNullOrBlank() }?.value
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
        private const val MAX_REDIRECTS = 5
    }
}

internal fun shouldExcludeForThirdPartyCredits(
    source: FeedSource,
    title: String,
    description: String?,
): Boolean = source.excludeThirdPartyCredits && carriesThirdPartyAgencyCredit(title, description)

private fun carriesThirdPartyAgencyCredit(title: String, description: String?): Boolean {
    val text = listOfNotNull(title, description).joinToString(" ")
    return LONG_AGENCY_CREDIT.containsMatchIn(text) ||
        VOA_SHARED_REPORTING_CREDIT.containsMatchIn(text) ||
        SHORT_AGENCY_CREDIT.containsMatchIn(text)
}

private const val TOKEN_START = "(?<![\\p{L}\\p{N}_])"
private const val TOKEN_END = "(?![\\p{L}\\p{N}_])"

private val LONG_AGENCY_CREDIT = Regex(
    "$TOKEN_START(?:(?:the\\s+)?associated\\s+press|reuters|agence\\s+france-presse)$TOKEN_END",
    RegexOption.IGNORE_CASE,
)
private val VOA_SHARED_REPORTING_CREDIT = Regex(
    "$TOKEN_START(?:some\\s+information\\s+for\\s+this\\s+report\\s+came\\s+from)$TOKEN_END" +
        ".{0,300}$TOKEN_START(?:AP|AFP)$TOKEN_END",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val SHORT_AGENCY_CREDIT = Regex(
    "(?:$TOKEN_START(?:from|via|by|according\\s+to|credited\\s+to|reporting\\s+by|" +
        "with\\s+reporting\\s+from|information\\s+from)$TOKEN_END.{0,80}" +
        "$TOKEN_START(?:AP|AFP)$TOKEN_END)|" +
        "(?:$TOKEN_START(?:AP|AFP)$TOKEN_END.{0,80}" +
        "$TOKEN_START(?:contributed|reported|reporting|provided|news\\s+agency)$TOKEN_END)",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val LABEL_TOKEN = Regex("[^\\p{L}\\p{N}_]+")

private val CATEGORY_ALIASES: Map<String, Set<String>> = buildMap {
    fun aliases(category: String, vararg names: String) {
        names.forEach { name -> put(name, setOf(category)) }
    }

    aliases("business", "business", "economy", "economics", "finance", "markets")
    aliases("business", "اقتصاد", "الاقتصاد", "اقتصادي", "اقتصادية", "اعمال", "أعمال", "اسواق", "أسواق")
    aliases("technology", "technology", "tech", "digital", "gadgets")
    aliases("technology", "تكنولوجيا", "تقنية", "التقنية", "تقنيه", "رقمي", "رقمية")
    aliases("science", "science", "environment", "space")
    aliases("science", "علوم", "العلوم", "علم", "بيئة", "البيئة", "بيئه", "فضاء")
    aliases("health", "health", "wellness", "medicine", "medical")
    aliases("health", "صحة", "الصحة", "صحه", "طب", "طبي", "طبية")
    aliases("sports", "sport", "sports", "football", "soccer", "cricket", "tennis")
    aliases("sports", "رياضة", "الرياضة", "رياضه", "رياضي", "رياضية", "رياضيه", "كرة")
    aliases("entertainment", "entertainment", "culture", "arts", "movies", "film", "television", "music", "style")
    aliases("entertainment", "ترفيه", "ثقافة", "الثقافة", "ثقافه", "فن", "الفن", "سينما", "موسيقى", "منوعات")
    put("science_and_health", setOf("science", "health"))
}
