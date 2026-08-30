package com.mk.newsshorts.server.ingest

import com.mk.newsshorts.server.store.ArticleStore
import com.mk.newsshorts.server.store.TextSource
import com.mk.newsshorts.server.summarize.ClassifyInput
import com.mk.newsshorts.server.summarize.Classifier
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

class ClassifyCycleTest {

    private fun store(): Pair<ArticleStore, File> {
        val db = File.createTempFile("classify-cycle", ".db").apply {
            delete()
            deleteOnExit()
        }
        return ArticleStore(db.absolutePath) to db
    }

    private fun ArticleStore.seedRendered(url: String, category: String = "general"): Long {
        val id = insertIfNew(
            title = "Headline",
            url = url,
            description = "Description",
            imageUrl = null,
            sourceName = "Source",
            language = "en",
            category = category,
            country = null,
            // Inside the retention window: runCycle prunes before it classifies.
            publishedAt = System.currentTimeMillis(),
        )!!
        putText(id, "en", "Rendered", "Summary", TextSource.AI)
        return id
    }

    private class ScriptedClassifier(private val answer: Set<String>?) : Classifier {
        val seen = mutableListOf<Long>()

        override suspend fun classify(batch: List<ClassifyInput>): Map<Long, Set<String>> {
            batch.forEach { seen += it.id }
            return if (answer == null) emptyMap() else batch.associate { it.id to answer }
        }
    }

    /** Fails the test if the pipeline sends an already-rendered article back for prose. */
    private object RefusingSummarizer : Summarizer {
        override suspend fun summarize(batch: List<SummaryInput>): Map<Long, SummaryOutput> {
            throw AssertionError("Re-summarized ${batch.map { it.id }} to classify it")
        }
    }

    private fun pipeline(
        store: ArticleStore,
        classifier: Classifier,
        summarizer: Summarizer = FallbackSummarizer(),
    ) = IngestionPipeline(
        store = store,
        fetcher = FeedFetcher { source -> SourceSnapshot(source, emptyList(), source.url) },
        summarizer = summarizer,
        classifier = classifier,
        maxSummariesPerCycle = 0,
        renderDelay = Duration.ZERO,
    )

    @Test
    fun `an already-rendered article is filed without being rewritten`() {
        runBlocking {
            val (store, db) = store()
            val id = store.seedRendered("https://example.com/backlog")
            val classifier = ScriptedClassifier(setOf("sports"))

            val report = pipeline(store, classifier, RefusingSummarizer).runCycle()

            assertEquals(1, report.articlesClassified)
            assertEquals(listOf(id), classifier.seen)
            assertEquals(listOf(id), store.feed("en", "sports", 10, 0).first.map { it.id })
            assertEquals("Rendered", store.feed("en", "sports", 10, 0).first.single().title)
            db.delete()
        }
    }

    @Test
    fun `a classified article is not offered again`() {
        runBlocking {
            val (store, db) = store()
            store.seedRendered("https://example.com/once")
            val classifier = ScriptedClassifier(setOf("health"))

            pipeline(store, classifier).runCycle()
            val afterFirstCycle = classifier.seen.size
            pipeline(store, classifier).runCycle()

            assertEquals(afterFirstCycle, classifier.seen.size)
            db.delete()
        }
    }

    @Test
    fun `a classifier that keeps failing stops being asked`() {
        runBlocking {
            val (store, db) = store()
            val id = store.seedRendered("https://example.com/unanswerable")
            val classifier = ScriptedClassifier(answer = null)

            repeat(4) { pipeline(store, classifier).runCycle() }

            // Three attempts, then the article settles in General rather than
            // spending a slot of every run for the week it stays in the feed.
            assertEquals(listOf(id, id, id), classifier.seen)
            assertEquals(listOf(id), store.feed("en", "general", 10, 0).first.map { it.id })
            db.delete()
        }
    }

    @Test
    fun `without a key nothing is filed and no attempt is spent`() {
        runBlocking {
            val (store, db) = store()
            val id = store.seedRendered("https://example.com/no-key")

            val report = IngestionPipeline(
                store = store,
                fetcher = FeedFetcher { source -> SourceSnapshot(source, emptyList(), source.url) },
                summarizer = FallbackSummarizer(),
                maxSummariesPerCycle = 0,
                renderDelay = Duration.ZERO,
            ).runCycle()

            assertEquals(0, report.articlesClassified)
            // The attempt budget is untouched, so configuring a key later still
            // files the whole backlog instead of finding it burnt through.
            assertTrue(store.pendingClassifications(10).any { it.id == id })
            db.delete()
        }
    }
}
