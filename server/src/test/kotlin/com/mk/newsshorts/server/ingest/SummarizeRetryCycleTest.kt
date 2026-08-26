package com.mk.newsshorts.server.ingest

import com.mk.newsshorts.server.store.ArticleStore
import com.mk.newsshorts.server.store.TextSource
import com.mk.newsshorts.server.summarize.ChainedSummarizer
import com.mk.newsshorts.server.summarize.FallbackSummarizer
import com.mk.newsshorts.server.summarize.Summarizer
import com.mk.newsshorts.server.summarize.SummaryInput
import com.mk.newsshorts.server.summarize.SummaryOutput
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummarizeRetryCycleTest {

    private fun store(): Pair<ArticleStore, File> {
        val db = File.createTempFile("summarize-retry", ".db").apply {
            delete()
            deleteOnExit()
        }
        return ArticleStore(db.absolutePath) to db
    }

    private class ScriptedPrimarySummarizer(vararg scripts: Map<Long, SummaryOutput>) : Summarizer {
        private val scripts = ArrayDeque(scripts.toList())

        override suspend fun summarize(batch: List<SummaryInput>): Map<Long, SummaryOutput> =
            scripts.removeFirstOrNull() ?: emptyMap()
    }

    @Test
    fun `fallback is served then retried and upgraded`() {
        runBlocking {
        val (store, db) = store()
        val articleId = store.insertIfNew(
            title = "Original title",
            url = "https://example.com/retry",
            description = "description text",
            imageUrl = null,
            sourceName = "Source",
            language = "en",
            category = "general",
            country = null,
            publishedAt = 2_000L,
        )!!
        val summarizer = ChainedSummarizer(
            ScriptedPrimarySummarizer(
                emptyMap(),
                mapOf(articleId to SummaryOutput("AI title", "AI summary", TextSource.AI)),
            ),
            FallbackSummarizer(),
        )
        val pipeline = IngestionPipeline(
            store,
            RssFetcher(),
            summarizer,
            maxSummariesPerCycle = 10,
            renderDelay = Duration.ZERO,
        )

        pipeline.summarizePending()

        val afterFallback = store.feed("en", "general", limit = 10, offset = 0).first.single()
        assertEquals("Original title", afterFallback.title)
        assertEquals("description text", afterFallback.summary)
        assertTrue(store.pendingTexts(10, emptySet()).any { it.id == articleId && it.targetLanguage == "en" })

        pipeline.summarizePending()

        val afterAi = store.feed("en", "general", limit = 10, offset = 0).first.single()
        assertEquals("AI title", afterAi.title)
        assertEquals("AI summary", afterAi.summary)
        assertTrue(store.pendingTexts(10, emptySet()).none { it.id == articleId && it.targetLanguage == "en" })
        db.delete()
        }
    }

    @Test
    fun `fallback text is not kept for translated targets`() {
        runBlocking {
        val (store, db) = store()
        val articleId = store.insertIfNew(
            title = "English title",
            url = "https://example.com/translation-fallback",
            description = "English description",
            imageUrl = null,
            sourceName = "Source",
            language = "en",
            category = "general",
            country = "eg",
            publishedAt = 3_000L,
        )!!
        val summarizer = ChainedSummarizer(
            ScriptedPrimarySummarizer(emptyMap(), emptyMap()),
            FallbackSummarizer(),
        )
        val pipeline = IngestionPipeline(
            store,
            RssFetcher(),
            summarizer,
            maxSummariesPerCycle = 10,
            renderDelay = Duration.ZERO,
        )

        pipeline.summarizePending()

        assertTrue(store.feed("ar", "general", limit = 10, offset = 0).first.none { it.id == articleId })
        assertTrue(store.pendingTexts(10, setOf("ar", "en")).any {
            it.id == articleId && it.targetLanguage == "ar"
        })

        pipeline.summarizePending()
        pipeline.summarizePending()

        assertTrue(store.feed("ar", "general", limit = 10, offset = 0).first.none { it.id == articleId })
        assertTrue(store.pendingTexts(10, setOf("ar", "en")).none {
            it.id == articleId && it.targetLanguage == "ar"
        })
        db.delete()
        }
    }

    @Test
    fun `cross-language fallback attempt can still be upgraded by AI`() {
        runBlocking {
        val (store, db) = store()
        val articleId = store.insertIfNew(
            title = "English title",
            url = "https://example.com/translation-upgrade",
            description = "English description",
            imageUrl = null,
            sourceName = "Source",
            language = "en",
            category = "general",
            country = "eg",
            publishedAt = 4_000L,
        )!!
        val summarizer = ChainedSummarizer(
            ScriptedPrimarySummarizer(
                emptyMap(),
                emptyMap(),
                emptyMap(),
                mapOf(articleId to SummaryOutput("Arabic title", "Arabic summary", TextSource.AI)),
            ),
            FallbackSummarizer(),
        )
        val pipeline = IngestionPipeline(
            store,
            RssFetcher(),
            summarizer,
            maxSummariesPerCycle = 10,
            renderDelay = Duration.ZERO,
        )

        pipeline.summarizePending()
        assertTrue(store.feed("ar", "general", limit = 10, offset = 0).first.none { it.id == articleId })
        assertTrue(store.pendingTexts(10, setOf("ar", "en")).any {
            it.id == articleId && it.targetLanguage == "ar"
        })

        pipeline.summarizePending()

        val translated = store.feed("ar", "general", limit = 10, offset = 0).first.single()
        assertEquals("Arabic title", translated.title)
        assertEquals("Arabic summary", translated.summary)
        assertTrue(store.pendingTexts(10, setOf("ar", "en")).none {
            it.id == articleId && it.targetLanguage == "ar"
        })
        db.delete()
        }
    }

    @Test
    fun `cross-language AI echoing the source title stays retryable`() {
        runBlocking {
        val (store, db) = store()
        val articleId = store.insertIfNew(
            title = "English title",
            url = "https://example.com/translation-title-echo",
            description = "English description",
            imageUrl = null,
            sourceName = "Source",
            language = "en",
            category = "general",
            country = "eg",
            publishedAt = 5_000L,
        )!!
        val summarizer = ChainedSummarizer(
            ScriptedPrimarySummarizer(
                mapOf(articleId to SummaryOutput("English title", "English AI summary", TextSource.AI)),
                mapOf(articleId to SummaryOutput("English title", "Arabic summary", TextSource.AI)),
            ),
            FallbackSummarizer(),
        )
        val pipeline = IngestionPipeline(
            store,
            RssFetcher(),
            summarizer,
            maxSummariesPerCycle = 10,
            renderDelay = Duration.ZERO,
        )

        pipeline.summarizePending()

        assertTrue(store.feed("ar", "general", limit = 10, offset = 0).first.none { it.id == articleId })
        assertTrue(store.pendingTexts(10, setOf("ar", "en")).any {
            it.id == articleId && it.targetLanguage == "ar"
        })
        db.delete()
        }
    }
}
