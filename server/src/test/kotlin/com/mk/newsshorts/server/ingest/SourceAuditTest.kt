package com.mk.newsshorts.server.ingest

import com.mk.newsshorts.server.model.FeedSource
import com.mk.newsshorts.server.model.RawArticle
import com.mk.newsshorts.server.config.FeedCatalog
import com.mk.newsshorts.server.store.ArticleStore
import com.mk.newsshorts.server.summarize.FallbackSummarizer
import com.mk.newsshorts.server.summarize.Summarizer
import com.mk.newsshorts.server.summarize.SummaryInput
import com.mk.newsshorts.server.summarize.SummaryOutput
import com.mk.newsshorts.server.store.TextSource
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceAuditTest {

    @Test
    fun `a category feed redirected to the general path is rejected`() {
        val sports = source("BBC عربي رياضة", "https://feeds.example/arabic/sports/rss.xml", "sports")

        val audit = auditSources(
            listOf(snapshot(sports, "https://feeds.example/arabic/rss.xml", "sports"))
        )

        assertTrue(audit.accepted.isEmpty())
        assertEquals(listOf(sports.name), audit.rejected.map { it.sourceName })
    }

    @Test
    fun `different category paths returning the same feed are both rejected`() {
        val science = source("Publisher Science", "https://example.com/rss/science", "science")
        val sports = source("Publisher Sport", "https://example.com/rss/sport", "sports")
        val urls = (1..5).map { "https://example.com/article/$it" }

        val audit = auditSources(
            listOf(
                snapshot(science, science.url, "science", urls),
                snapshot(sports, sports.url, "sports", urls),
            )
        )

        assertTrue(audit.accepted.isEmpty())
        assertEquals(setOf(science.name, sports.name), audit.rejected.map { it.sourceName }.toSet())
    }

    @Test
    fun `duplicate specialised feeds are rejected even within one category`() {
        val culture = source("Publisher Culture", "https://example.com/rss/culture", "entertainment")
        val style = source("Publisher Style", "https://example.com/rss/style", "entertainment")
        val urls = (1..5).map { "https://example.com/article/$it" }

        val audit = auditSources(
            listOf(
                snapshot(culture, culture.url, "culture", urls),
                snapshot(style, style.url, "style", urls),
            )
        )

        assertTrue(audit.accepted.isEmpty())
        assertEquals(setOf(culture.name, style.name), audit.rejected.map { it.sourceName }.toSet())
    }

    @Test
    fun `section paths sharing nearly every article are both rejected`() {
        val science = source("Publisher Science", "https://example.com/rss/science", "science")
        val sports = source("Publisher Sport", "https://example.com/rss/sport", "sports")
        val shared = (1..9).map { "https://example.com/article/$it" }

        // The real case this was written for: one home-page feed answering both
        // section paths, fetched a moment apart, so one list has an article the
        // other has not caught up with yet.
        val audit = auditSources(
            listOf(
                snapshot(science, science.url, "science", shared + "https://example.com/article/late"),
                snapshot(sports, sports.url, "sports", shared),
            )
        )

        assertEquals(setOf(science.name, sports.name), audit.rejected.map { it.sourceName }.toSet())
    }

    @Test
    fun `sections that merely cross-post a story are both kept`() {
        val technology = source("Publisher Tech", "https://example.com/rss/technology", "technology")
        val business = source("Publisher Business", "https://example.com/rss/business", "business")
        val crossPosted = "https://example.com/article/chip-maker-results"

        val audit = auditSources(
            listOf(
                snapshot(technology, technology.url, "tech", (1..5).map { "https://example.com/t/$it" } + crossPosted),
                snapshot(business, business.url, "business", (1..5).map { "https://example.com/b/$it" } + crossPosted),
            )
        )

        assertEquals(emptyList(), audit.rejected)
    }

    @Test
    fun `a host or protocol redirect preserving the section path is accepted`() {
        val sports = source("Sport", "http://www.example.com/rss/sport/", "sports")
        val result = snapshot(sports, "https://example.com/rss/sport", "sports")

        assertEquals(listOf(result), auditSources(listOf(result)).accepted)
    }

    @Test
    fun `general feeds may move because they grant no specialised membership`() {
        val general = source("General", "https://example.com/old.xml", "general")
        val result = snapshot(general, "https://example.com/new.xml", "general")

        assertEquals(listOf(result), auditSources(listOf(result)).accepted)
    }

    @Test
    fun `a rejected source keeps its articles and loses its section`() {
        runBlocking {
            val rejected = FeedCatalog.sources.first { it.category == "sports" }
            val db = File.createTempFile("source-audit-ingestion", ".db").apply {
                delete()
                deleteOnExit()
            }
            val store = ArticleStore(db.absolutePath)
            val fetcher = FeedFetcher { source ->
                if (source == rejected) {
                    snapshot(
                        source,
                        effectiveUrl = "https://${java.net.URI(source.url).host}/rss.xml",
                        title = "Rejected",
                    )
                } else {
                    SourceSnapshot(source, emptyList(), source.url)
                }
            }
            val report = IngestionPipeline(
                store = store,
                fetcher = fetcher,
                summarizer = FallbackSummarizer(),
                maxSummariesPerCycle = 0,
                renderDelay = Duration.ZERO,
            ).runCycle()

            assertEquals(listOf(rejected.name), report.sourcesRejected)
            // The news is real even when the section label is not: throwing the
            // articles away would punish the label at the reader's expense.
            assertEquals(1, report.articlesInserted)
            assertTrue(store.feed("ar", rejected.category, 10, 0).first.isEmpty())
            db.delete()
        }
    }

    @Test
    fun `article taxonomy lets a general source contribute after content agreement`() {
        runBlocking {
            val general = FeedCatalog.sources.first { it.category == "general" && it.language == "ar" }
            val db = File.createTempFile("general-source-category", ".db").apply {
                delete()
                deleteOnExit()
            }
            val store = ArticleStore(db.absolutePath)
            val fetcher = FeedFetcher { source ->
                if (source == general) {
                    SourceSnapshot(
                        source = source,
                        effectiveUrl = source.url,
                        articles = listOf(
                            RawArticle(
                                title = "فوز في المباراة النهائية",
                                url = "https://example.com/sports/final",
                                description = "حقق الفريق الفوز في المباراة النهائية.",
                                imageUrl = null,
                                publishedAtMillis = System.currentTimeMillis(),
                                source = source,
                                candidateCategories = setOf("sports"),
                            )
                        ),
                    )
                } else {
                    SourceSnapshot(source, emptyList(), source.url)
                }
            }
            val summarizer = object : Summarizer {
                override suspend fun summarize(batch: List<SummaryInput>): Map<Long, SummaryOutput> =
                    batch.associate { input ->
                        input.id to SummaryOutput(
                            title = if (input.targetLanguage == "en") "Victory in the final" else input.title,
                            summary = if (input.targetLanguage == "en") {
                                "A reliable sports summary"
                            } else {
                                "ملخص رياضي موثوق"
                            },
                            source = TextSource.AI,
                            categories = setOf("sports"),
                        )
                    }
            }

            val pipeline = IngestionPipeline(
                store = store,
                fetcher = fetcher,
                summarizer = summarizer,
                maxSummariesPerCycle = 10,
                renderDelay = Duration.ZERO,
            )
            val report = pipeline.runCycle()

            assertEquals(1, report.articlesInserted)
            assertEquals(1, store.feed("ar", "sports", 10, 0).first.size)

            // The translation waits for the category to be confirmed rather
            // than for a feed to claim one, so it lands on the next cycle. That
            // delay is the price of not rendering every article twice on the
            // strength of a label that may be wrong.
            assertTrue(store.feed("en", "sports", 10, 0).first.isEmpty())
            pipeline.runCycle()
            assertEquals(1, store.feed("en", "sports", 10, 0).first.size)
            db.delete()
        }
    }

    private fun source(name: String, url: String, category: String) =
        FeedSource(name, url, "ar", category)

    private fun snapshot(
        source: FeedSource,
        effectiveUrl: String,
        title: String,
        urls: List<String> = listOf("https://example.com/$title"),
    ) = SourceSnapshot(
        source = source,
        effectiveUrl = effectiveUrl,
        articles = urls.mapIndexed { index, url ->
            RawArticle(
                title = "$title $index",
                url = url,
                description = "Description",
                imageUrl = null,
                publishedAtMillis = index.toLong(),
                source = source,
            )
        },
    )
}
