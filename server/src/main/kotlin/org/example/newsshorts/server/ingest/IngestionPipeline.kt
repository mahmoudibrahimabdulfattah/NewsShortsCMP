package org.example.newsshorts.server.ingest

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.newsshorts.server.config.FeedCatalog
import org.example.newsshorts.server.store.ArticleStore
import org.example.newsshorts.server.summarize.GeminiSummarizer
import org.example.newsshorts.server.summarize.Summarizer
import org.example.newsshorts.server.summarize.SummaryInput
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * fetch RSS -> insert new articles -> summarize pending -> repeat.
 */
class IngestionPipeline(
    private val store: ArticleStore,
    private val fetcher: RssFetcher,
    private val summarizer: Summarizer,
    private val refreshMinutes: Int = (System.getenv("REFRESH_MINUTES") ?: "20").toInt(),
    private val maxSummariesPerCycle: Int = (System.getenv("MAX_SUMMARIES_PER_CYCLE") ?: "60").toInt(),
) {

    private val log = LoggerFactory.getLogger(IngestionPipeline::class.java)

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                try {
                    runCycle()
                } catch (e: Exception) {
                    log.error("Ingestion cycle failed", e)
                }
                delay(refreshMinutes.minutes)
            }
        }
    }

    suspend fun runCycle() {
        var inserted = 0
        FeedCatalog.sources.forEach { source ->
            val articles = fetcher.fetch(source)
            articles.forEach { article ->
                val id = store.insertIfNew(
                    title = article.title,
                    url = article.url,
                    description = article.description,
                    imageUrl = article.imageUrl,
                    sourceName = source.name,
                    language = source.language,
                    category = source.category,
                    country = source.country,
                    publishedAt = article.publishedAtMillis,
                )
                if (id != null) inserted++
            }
        }
        log.info("Fetched ${FeedCatalog.sources.size} feeds, $inserted new articles")
        summarizePending()
    }

    private suspend fun summarizePending() {
        val pending = store.pendingSummaries(maxSummariesPerCycle)
        if (pending.isEmpty()) return
        log.info("Summarizing ${pending.size} articles")

        // Batch per language, sequential requests with a gap to respect
        // the Gemini free tier's ~15 RPM limit.
        pending
            .map { SummaryInput(it.id, it.title, it.description, it.language) }
            .groupBy { it.language }
            .forEach { (_, articles) ->
                articles.chunked(GeminiSummarizer.BATCH_SIZE).forEach { chunk ->
                    val summaries = summarizer.summarize(chunk)
                    summaries.forEach { (id, summary) -> store.setSummary(id, summary) }
                    delay(5.seconds)
                }
            }
    }
}
