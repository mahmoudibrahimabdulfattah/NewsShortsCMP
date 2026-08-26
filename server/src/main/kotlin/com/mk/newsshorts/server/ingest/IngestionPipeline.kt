package com.mk.newsshorts.server.ingest

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mk.newsshorts.server.config.FeedCatalog
import com.mk.newsshorts.server.store.ArticleStore
import com.mk.newsshorts.server.summarize.GeminiSummarizer
import com.mk.newsshorts.server.summarize.Summarizer
import com.mk.newsshorts.server.summarize.SummaryInput
import com.mk.newsshorts.server.store.TextSource
import com.mk.newsshorts.server.store.TextWriteResult
import org.slf4j.LoggerFactory
import kotlin.time.Duration
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
    private val renderDelay: Duration = 5.seconds,
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
        val emptySources = mutableListOf<String>()
        FeedCatalog.sources.forEach { source ->
            val articles = fetcher.fetch(source)
            // Sites behind bot protection answer 200 with an HTML challenge, so
            // a dead source looks identical to a quiet one unless it is named.
            if (articles.isEmpty()) emptySources += source.name
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
        if (emptySources.isNotEmpty()) {
            log.warn("${emptySources.size} feeds returned nothing: ${emptySources.joinToString()}")
        }

        val pruned = store.prune(System.currentTimeMillis() - retentionDays * MILLIS_PER_DAY)
        if (pruned > 0) log.info("Pruned $pruned articles older than $retentionDays days")

        summarizePending()
    }

    internal suspend fun summarizePending() {
        val pending = store.pendingTexts(maxSummariesPerCycle, FeedCatalog.countryLanguages)
        if (pending.isEmpty()) return
        log.info("Rendering ${pending.size} article texts")

        // Batch per target language, sequential requests with a gap to respect
        // the Gemini free tier's ~15 RPM limit.
        pending
            .groupBy { it.targetLanguage }
            .forEach { (targetLanguage, articles) ->
                val chunks = articles.chunked(GeminiSummarizer.BATCH_SIZE)
                chunks.forEach { chunk ->
                    val rendered = summarizer.summarize(
                        chunk.map { SummaryInput(it.id, it.title, it.description, targetLanguage) }
                    )
                    // The fallback can't translate, so only keep what it
                    // produced when the article is already in this language.
                    chunk.forEach { article ->
                        val text = rendered[article.id] ?: return@forEach
                        val untranslatedCrossLanguage =
                            article.sourceLanguage != targetLanguage &&
                                (text.source == TextSource.FALLBACK || text.title == article.title)
                        if (untranslatedCrossLanguage) {
                            // A dropped cross-language render still has to age
                            // out of the retry queue. Leaving it rowless makes
                            // it look fresh on every scheduled run, which lets
                            // repeat 429s spend the first-render budget forever.
                            when (
                                store.recordUnservedTextAttempt(
                                    article.id,
                                    targetLanguage,
                                    article.title,
                                    article.description.orEmpty(),
                                )
                            ) {
                                TextWriteResult.RECORDED_UNSERVED ->
                                    log.info("Recorded unserved text attempt for article ${article.id} in $targetLanguage")

                                TextWriteResult.RETAINED_AI -> Unit

                                TextWriteResult.INSERTED_AI,
                                TextWriteResult.INSERTED_FALLBACK,
                                TextWriteResult.UPDATED_FALLBACK,
                                TextWriteResult.UPGRADED_TO_AI -> Unit
                            }
                            return@forEach
                        }
                        when (store.putText(article.id, targetLanguage, text.title, text.summary, text.source)) {
                            TextWriteResult.INSERTED_FALLBACK, TextWriteResult.UPDATED_FALLBACK ->
                                log.info("Stored fallback text for article ${article.id} in $targetLanguage after a retryable attempt")

                            TextWriteResult.UPGRADED_TO_AI ->
                                log.info("Upgraded fallback text for article ${article.id} in $targetLanguage")

                            TextWriteResult.INSERTED_AI,
                            TextWriteResult.RETAINED_AI,
                            TextWriteResult.RECORDED_UNSERVED -> Unit
                        }
                    }
                    delay(renderDelay)
                }
            }
    }

    private companion object {
        const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
}
