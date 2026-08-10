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
    private val retentionDays: Long = (System.getenv("RETENTION_DAYS") ?: "4").toLong(),
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

        val pruned = store.prune(System.currentTimeMillis() - retentionDays * MILLIS_PER_DAY)
        if (pruned > 0) log.info("Pruned $pruned articles older than $retentionDays days")

        summarizePending()
    }

    private suspend fun summarizePending() {
        val pending = store.pendingTexts(maxSummariesPerCycle, FeedCatalog.countryLanguages)
        if (pending.isEmpty()) return
        log.info("Rendering ${pending.size} article texts")

        // Batch per target language, sequential requests with a gap to respect
        // the Gemini free tier's ~15 RPM limit.
        pending
            .groupBy { it.targetLanguage }
            .forEach { (targetLanguage, articles) ->
                articles.chunked(GeminiSummarizer.BATCH_SIZE).forEach { chunk ->
                    val rendered = summarizer.summarize(
                        chunk.map { SummaryInput(it.id, it.title, it.description, targetLanguage) }
                    )
                    // The fallback can't translate, so only keep what it
                    // produced when the article is already in this language.
                    chunk.forEach { article ->
                        val text = rendered[article.id] ?: return@forEach
                        if (text.title == article.title && article.sourceLanguage != targetLanguage) return@forEach
                        store.putText(article.id, targetLanguage, text.title, text.summary)
                    }
                    delay(5.seconds)
                }
            }
    }

    private companion object {
        const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
}
