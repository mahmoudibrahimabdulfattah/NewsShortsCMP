package com.mk.newsshorts.server.ingest

import com.mk.newsshorts.server.config.FeedCatalog
import com.mk.newsshorts.server.model.RawArticle
import com.mk.newsshorts.server.store.ArticleStore
import com.mk.newsshorts.server.summarize.FallbackSummarizer
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountryFeedTest {

    @Test
    fun `Gulf sources fill UAE without leaving Saudi Arabia`() {
        val uaeSources = FeedCatalog.sources
            .filter { "ae" in it.countries }
            .associateBy { it.name }

        assertEquals(
            setOf("الشرق الأوسط", "Saudi Gazette", "VOA Middle East", "The National"),
            uaeSources.keys,
        )
        assertEquals(
            setOf("الشرق الأوسط", "Saudi Gazette"),
            FeedCatalog.sources.filter { "sa" in it.countries }.mapTo(linkedSetOf()) { it.name },
        )
        assertEquals("sa", uaeSources.getValue("الشرق الأوسط").country)
        assertEquals("sa", uaeSources.getValue("Saudi Gazette").country)
        assertNull(uaeSources.getValue("VOA Middle East").country)
    }

    @Test
    fun `catalog contains every country offered by the client`() {
        val clientCountries = setOf(
            "us", "gb", "eg", "sa", "ae", "de", "fr",
            "in", "cn", "jp", "au", "ca", "br",
        )

        assertEquals(clientCountries, FeedCatalog.countries)
    }

    @Test
    fun `a countryless regional source stays in the general feed`() = runBlocking {
        // VOA Middle East has no country of its own but serves the UAE tab.
        // Tagging it with a country would drop it out of the general feed,
        // which excludes country-tagged articles.
        val source = FeedCatalog.sources.single { it.name == "VOA Middle East" }
        val db = File.createTempFile("countryless-feed", ".db").apply {
            delete()
            deleteOnExit()
        }
        val store = ArticleStore(db.absolutePath)
        val article = RawArticle(
            title = "Regional headline",
            url = "https://example.com/regional-headline",
            description = "Regional description",
            imageUrl = null,
            publishedAtMillis = System.currentTimeMillis(),
            source = source,
        )
        val pipeline = IngestionPipeline(
            store = store,
            fetcher = FeedFetcher { requested ->
                SourceSnapshot(
                    source = requested,
                    articles = if (requested == source) listOf(article) else emptyList(),
                    effectiveUrl = requested.url,
                )
            },
            summarizer = FallbackSummarizer(),
            maxSummariesPerCycle = 10,
            maxClassificationsPerCycle = 0,
            renderDelay = Duration.ZERO,
        )

        pipeline.runCycle()

        val general = store.feed(
            "en", null, limit = 10, offset = 0, country = null, excludeCountryTagged = true,
        )
        val uae = store.feed("en", null, limit = 10, offset = 0, country = "ae")

        assertEquals(1, general.second)
        assertEquals(1, uae.second)
        assertEquals(general.first.map { it.id }, uae.first.map { it.id })
        db.delete()
        Unit
    }

    @Test
    fun `one regional article appears once in each country feed`() = runBlocking {
        val source = FeedCatalog.sources.single { it.name == "الشرق الأوسط" }
        val db = File.createTempFile("country-feed", ".db").apply {
            delete()
            deleteOnExit()
        }
        val store = ArticleStore(db.absolutePath)
        val article = RawArticle(
            title = "Gulf headline",
            url = "https://example.com/gulf-headline",
            description = "Gulf description",
            imageUrl = null,
            publishedAtMillis = System.currentTimeMillis(),
            source = source,
        )
        val pipeline = IngestionPipeline(
            store = store,
            fetcher = FeedFetcher { requested ->
                SourceSnapshot(
                    source = requested,
                    articles = if (requested == source) listOf(article) else emptyList(),
                    effectiveUrl = requested.url,
                )
            },
            summarizer = FallbackSummarizer(),
            maxSummariesPerCycle = 10,
            maxClassificationsPerCycle = 0,
            renderDelay = Duration.ZERO,
        )

        pipeline.runCycle()
        pipeline.runCycle()

        val sa = store.feed("ar", null, limit = 10, offset = 0, country = "sa")
        val ae = store.feed("ar", null, limit = 10, offset = 0, country = "ae")

        assertEquals(1, sa.second)
        assertEquals(1, ae.second)
        assertEquals(sa.first.map { it.id }, ae.first.map { it.id })
        assertEquals(ae.first.size, ae.first.map { it.id }.toSet().size)
        assertTrue(ae.first.isNotEmpty())
        db.delete()
        Unit
    }
}
