package com.mk.newsshorts.server.ingest

import com.mk.newsshorts.server.model.FeedSource
import com.mk.newsshorts.server.model.RawArticle
import com.mk.newsshorts.server.model.SourceMode
import com.mk.newsshorts.server.model.summarySourceNames
import com.mk.newsshorts.server.store.ArticleStore
import com.mk.newsshorts.server.store.TextSource
import com.mk.newsshorts.server.summarize.Summarizer
import com.mk.newsshorts.server.summarize.SummaryInput
import com.mk.newsshorts.server.summarize.SummaryOutput
import java.io.File
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class SourcePolicyModeTest {

    private fun store(): Pair<ArticleStore, File> {
        val db = File.createTempFile("source-policy-mode", ".db").apply {
            delete()
            deleteOnExit()
        }
        return ArticleStore(db.absolutePath) to db
    }

    private class RecordingFetcher(
        private val snapshots: Map<String, SourceSnapshot> = emptyMap(),
    ) : FeedFetcher {
        val fetched = mutableListOf<String>()

        override suspend fun fetch(source: FeedSource): SourceSnapshot {
            fetched += source.name
            return snapshots[source.name] ?: SourceSnapshot(source, emptyList(), source.url)
        }
    }

    private class RecordingSummarizer : Summarizer {
        val seen = mutableListOf<SummaryInput>()

        override suspend fun summarize(batch: List<SummaryInput>): Map<Long, SummaryOutput> {
            seen += batch
            return batch.associate { input ->
                input.id to SummaryOutput(
                    title = "AI ${input.title}",
                    summary = "AI ${input.description}",
                    source = TextSource.AI,
                    category = "general",
                )
            }
        }
    }

    private object RefusingSummarizer : Summarizer {
        override suspend fun summarize(batch: List<SummaryInput>): Map<Long, SummaryOutput> {
            throw AssertionError("Unexpected summary request for ${batch.map { it.id }}")
        }
    }

    @Test
    fun `source defaults are blocked without image permission`() {
        val unknown = FeedSource("Unknown Source", "https://feeds.example/unknown.xml", "en", "general")

        assertEquals(SourceMode.BLOCKED, unknown.mode)
        assertEquals(false, unknown.allowsPublisherImages)
    }

    @Test
    fun `blocked sources are never fetched`() {
        runBlocking {
        val (store, db) = store()
        val blocked = source("Blocked Source", SourceMode.BLOCKED)
        val summary = source("Summary Source", SourceMode.SUMMARY)
        val fetcher = RecordingFetcher()

        val report = IngestionPipeline(
            store = store,
            fetcher = fetcher,
            summarizer = RefusingSummarizer,
            sources = listOf(blocked, summary),
            maxSummariesPerCycle = 0,
            renderDelay = Duration.ZERO,
        ).runCycle()

        assertEquals(2, report.sourcesTotal)
        assertEquals(listOf(summary.name), fetcher.fetched)
        db.delete()
        }
    }

    @Test
    fun `blocked source rows are not published`() {
        val (store, db) = store()
        val blocked = source("Blocked Source", SourceMode.BLOCKED)
        val summary = source("Summary Source", SourceMode.SUMMARY)
        val id = store.insertIfNew(
            title = "Blocked headline",
            url = "https://example.com/blocked",
            description = "Blocked description",
            imageUrl = "https://example.com/blocked.jpg",
            sourceName = blocked.name,
            language = "en",
            category = "general",
            country = null,
            publishedAt = 2_000L,
        )!!
        store.putText(id, "en", "Blocked headline", "Blocked summary", TextSource.AI)
        store.recordClassificationAttempt(id, "general")

        val feed = store.feed(
            language = "en",
            category = "general",
            limit = 10,
            offset = 0,
            publishableSourceNames = setOf(summary.name),
        )

        assertTrue(feed.first.isEmpty())
        assertEquals(0L, feed.second)
        db.delete()
    }

    @Test
    fun `headline source stores only headline text and publishes in source category`() {
        runBlocking {
        val (store, db) = store()
        val headline = source("Headline Source", SourceMode.HEADLINE, category = "sports")
        val summarySourceNames = listOf(headline).summarySourceNames()
        val articleUrl = "https://example.com/headline"
        val fetcher = RecordingFetcher(
            mapOf(
                headline.name to SourceSnapshot(
                    source = headline,
                    articles = listOf(
                        rawArticle(
                            source = headline,
                            title = "Supplied headline",
                            url = articleUrl,
                            description = "Publisher description",
                            imageUrl = "https://example.com/image.jpg",
                        )
                    ),
                    effectiveUrl = headline.url,
                )
            )
        )

        val report = IngestionPipeline(
            store = store,
            fetcher = fetcher,
            summarizer = RefusingSummarizer,
            sources = listOf(headline),
            maxSummariesPerCycle = 10,
            renderDelay = Duration.ZERO,
        ).runCycle()
        val stored = storedArticle(db, articleUrl)
        val textRows = storedTexts(db, stored.id)
        val sportsFeed = store.feed(
            language = "en",
            category = "sports",
            limit = 10,
            offset = 0,
            publishableSourceNames = setOf(headline.name),
        ).first

        assertEquals(1, report.articlesInserted)
        assertNull(stored.description)
        assertNull(stored.imageUrl)
        assertEquals(listOf(StoredText("en", "Supplied headline", "", TextSource.HEADLINE)), textRows)
        assertEquals(1, sportsFeed.size)
        assertEquals("Supplied headline", sportsFeed.single().title)
        assertEquals("", sportsFeed.single().summary)
        assertNull(sportsFeed.single().imageUrl)
        assertTrue(
            store.feed("ar", "sports", 10, 0, publishableSourceNames = setOf(headline.name)).first.isEmpty()
        )
        assertEquals(emptySet<String>(), summarySourceNames)
        assertTrue(
            store.pendingTexts(10, setOf("ar", "en"), summarySourceNames = summarySourceNames)
                .none { it.id == stored.id }
        )
        assertTrue(
            store.pendingClassifications(10, summarySourceNames = summarySourceNames)
                .none { it.id == stored.id }
        )
        assertTrue(store.pendingTexts(10, setOf("ar", "en"), summarySourceNames = emptySet()).isEmpty())
        db.delete()
        }
    }

    @Test
    fun `pending texts filters non-summary sources before limit`() {
        val (store, db) = store()
        val blocked = source("Blocked Source", SourceMode.BLOCKED)
        val summary = source("Summary Source", SourceMode.SUMMARY)
        store.insertIfNew(
            title = "New blocked",
            url = "https://example.com/new-blocked",
            description = "Blocked body",
            imageUrl = null,
            sourceName = blocked.name,
            language = "en",
            category = "general",
            country = null,
            publishedAt = 2_000L,
        )!!
        val summaryId = store.insertIfNew(
            title = "Older summary",
            url = "https://example.com/older-summary",
            description = "Summary body",
            imageUrl = null,
            sourceName = summary.name,
            language = "en",
            category = "general",
            country = null,
            publishedAt = 1_000L,
        )!!

        val pending = store.pendingTexts(
            limit = 1,
            countryLanguages = emptySet(),
            summarySourceNames = setOf(summary.name),
        )

        assertEquals(listOf(summaryId), pending.map { it.id })
        db.delete()
    }

    @Test
    fun `summary mode does not imply image permission`() {
        runBlocking {
        val (store, db) = store()
        val summary = source(
            name = "Summary Without Images",
            mode = SourceMode.SUMMARY,
            allowsPublisherImages = false,
        )
        val articleUrl = "https://example.com/no-image-permission"
        val fetcher = RecordingFetcher(
            mapOf(summary.name to snapshot(summary, rawArticle(summary, url = articleUrl)))
        )

        IngestionPipeline(
            store = store,
            fetcher = fetcher,
            summarizer = RefusingSummarizer,
            sources = listOf(summary),
            maxSummariesPerCycle = 0,
            renderDelay = Duration.ZERO,
        ).runCycle()
        val stored = storedArticle(db, articleUrl)

        assertEquals("Publisher description", stored.description)
        assertNull(stored.imageUrl)
        db.delete()
        }
    }

    @Test
    fun `summary sources still store source content and render AI text`() {
        runBlocking {
        val (store, db) = store()
        val summary = source("Summary Source", SourceMode.SUMMARY, allowsPublisherImages = true)
        val articleUrl = "https://example.com/summary"
        val fetcher = RecordingFetcher(
            mapOf(summary.name to snapshot(summary, rawArticle(summary, url = articleUrl)))
        )
        val summarizer = RecordingSummarizer()

        val report = IngestionPipeline(
            store = store,
            fetcher = fetcher,
            summarizer = summarizer,
            sources = listOf(summary),
            maxSummariesPerCycle = 10,
            renderDelay = Duration.ZERO,
        ).runCycle()
        val stored = storedArticle(db, articleUrl)
        val feed = store.feed(
            language = "en",
            category = "general",
            limit = 10,
            offset = 0,
            publishableSourceNames = setOf(summary.name),
        ).first.single()

        assertEquals(1, report.articlesInserted)
        assertEquals(listOf("Supplied headline"), summarizer.seen.map { it.title })
        assertEquals("Publisher description", stored.description)
        assertEquals("https://example.com/image.jpg", stored.imageUrl)
        assertEquals("AI Supplied headline", feed.title)
        assertEquals("AI Publisher description", feed.summary)
        assertEquals("https://example.com/image.jpg", feed.imageUrl)
        assertTrue(
            store.pendingTexts(10, emptySet(), summarySourceNames = setOf(summary.name))
                .none { it.id == stored.id }
        )
        db.delete()
        }
    }

    private fun source(
        name: String,
        mode: SourceMode,
        category: String = "general",
        allowsPublisherImages: Boolean = true,
    ) = FeedSource(
        name = name,
        url = "https://feeds.example/${name.lowercase().replace(' ', '-')}.xml",
        language = "en",
        category = category,
        mode = mode,
        allowsPublisherImages = allowsPublisherImages,
    )

    private fun snapshot(source: FeedSource, article: RawArticle) = SourceSnapshot(
        source = source,
        articles = listOf(article),
        effectiveUrl = source.url,
    )

    private fun rawArticle(
        source: FeedSource,
        title: String = "Supplied headline",
        url: String,
        description: String? = "Publisher description",
        imageUrl: String? = "https://example.com/image.jpg",
    ) = RawArticle(
        title = title,
        url = url,
        description = description,
        imageUrl = imageUrl,
        publishedAtMillis = System.currentTimeMillis(),
        source = source,
    )

    private data class StoredArticle(val id: Long, val description: String?, val imageUrl: String?)

    private fun storedArticle(db: File, url: String): StoredArticle =
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
            connection.prepareStatement(
                "SELECT id, description, image_url FROM articles WHERE url = ?"
            ).use { statement ->
                statement.setString(1, url)
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next(), "missing stored article for $url")
                    StoredArticle(
                        id = rows.getLong("id"),
                        description = rows.getString("description"),
                        imageUrl = rows.getString("image_url"),
                    )
                }
            }
        }

    private data class StoredText(
        val language: String,
        val title: String,
        val summary: String,
        val source: TextSource,
    )

    private fun storedTexts(db: File, articleId: Long): List<StoredText> =
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
            connection.prepareStatement(
                """
                SELECT language, title, summary, source
                FROM article_texts
                WHERE article_id = ?
                ORDER BY language
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, articleId)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                StoredText(
                                    language = rows.getString("language"),
                                    title = rows.getString("title"),
                                    summary = rows.getString("summary"),
                                    source = TextSource.valueOf(rows.getString("source")),
                                )
                            )
                        }
                    }
                }
            }
        }
}
