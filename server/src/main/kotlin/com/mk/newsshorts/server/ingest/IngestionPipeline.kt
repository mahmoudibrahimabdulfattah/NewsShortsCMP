package com.mk.newsshorts.server.ingest

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mk.newsshorts.server.config.FeedCatalog
import com.mk.newsshorts.core.contract.feed.NewsCategories
import com.mk.newsshorts.server.store.ArticleStore
import com.mk.newsshorts.server.summarize.ClassifyInput
import com.mk.newsshorts.server.summarize.Classifier
import com.mk.newsshorts.server.summarize.GeminiClassifier
import com.mk.newsshorts.server.summarize.GeminiSummarizer
import com.mk.newsshorts.server.summarize.NoClassifier
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
data class CycleReport(
    val sourcesTotal: Int,
    val sourcesEmpty: Int,
    val sourcesRejected: List<String>,
    val articlesInserted: Int,
    val textsRendered: Int,
    val textsFailed: Int,
    val articlesClassified: Int,
)

internal data class TextRenderReport(val rendered: Int, val failed: Int)

class IngestionPipeline(
    private val store: ArticleStore,
    private val fetcher: FeedFetcher,
    private val summarizer: Summarizer,
    // Defaults to the classifier that answers nothing, which is what a
    // deployment without a Gemini key gets: every article stays in General
    // rather than being filed by whichever feed happened to carry it.
    private val classifier: Classifier = NoClassifier,
    private val refreshMinutes: Int = (System.getenv("REFRESH_MINUTES") ?: "20").toInt(),
    private val maxSummariesPerCycle: Int = (System.getenv("MAX_SUMMARIES_PER_CYCLE") ?: "60").toInt(),
    private val maxClassificationsPerCycle: Int =
        (System.getenv("MAX_CLASSIFICATIONS_PER_CYCLE") ?: "600").toInt(),
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

    suspend fun runCycle(): CycleReport {
        var inserted = 0
        val emptySources = mutableListOf<String>()
        val snapshots = FeedCatalog.sources.map { source -> fetcher.fetch(source) }
        val audit = auditSources(snapshots)
        val rejectedSections = audit.rejected.mapTo(mutableSetOf()) { it.sourceName }
        snapshots.forEach { snapshot ->
            val source = snapshot.source
            val articles = snapshot.articles
            // Sites behind bot protection answer 200 with an HTML challenge, so
            // a dead source looks identical to a quiet one unless it is named.
            if (articles.isEmpty()) emptySources += source.name
            // A rejected feed is still carrying the publisher's real news — it
            // is only lying about which section it is. Dropping it would throw
            // away the articles to punish the label, so the section claim is
            // discarded and each article's own evidence is kept.
            val claimed = if (source.name in rejectedSections) emptySet() else source.categories
            articles.forEach { article ->
                val candidates = (claimed + article.candidateCategories)
                    .filterTo(linkedSetOf(), NewsCategories.all::contains)
                    .ifEmpty { linkedSetOf(NewsCategories.GENERAL) }
                var articleInserted = false
                candidates.forEach { category ->
                    val id = store.insertIfNew(
                        title = article.title,
                        url = article.url,
                        description = article.description,
                        imageUrl = article.imageUrl,
                        sourceName = source.name,
                        language = source.language,
                        category = category,
                        country = source.country,
                        publishedAt = article.publishedAtMillis,
                    )
                    if (id != null) articleInserted = true
                }
                if (articleInserted) inserted++
            }
        }
        log.info("Fetched ${FeedCatalog.sources.size} feeds, $inserted new articles")
        if (emptySources.isNotEmpty()) {
            log.warn("${emptySources.size} feeds returned nothing: ${emptySources.joinToString()}")
        }
        if (audit.rejected.isNotEmpty()) {
            log.warn(
                "${audit.rejected.size} feeds rejected: " +
                    audit.rejected.joinToString { "${it.sourceName} (${it.reason})" }
            )
        }

        val pruned = store.prune(System.currentTimeMillis() - retentionDays * MILLIS_PER_DAY)
        if (pruned > 0) log.info("Pruned $pruned articles older than $retentionDays days")

        val texts = summarizePending()
        val classified = classifyPending()
        return CycleReport(
            sourcesTotal = FeedCatalog.sources.size,
            sourcesEmpty = emptySources.size,
            sourcesRejected = audit.rejected.map { it.sourceName },
            articlesInserted = inserted,
            textsRendered = texts.rendered,
            textsFailed = texts.failed,
            articlesClassified = classified,
        )
    }

    internal suspend fun summarizePending(): TextRenderReport {
        val pending = store.pendingTexts(maxSummariesPerCycle, FeedCatalog.countryLanguages)
        if (pending.isEmpty()) return TextRenderReport(rendered = 0, failed = 0)
        log.info("Rendering ${pending.size} article texts")
        var renderedCount = 0
        var failedCount = 0

        // Batch per target language, sequential requests with a gap to respect
        // the Gemini free tier's ~15 RPM limit.
        pending
            .groupBy { it.targetLanguage }
            .forEach { (targetLanguage, articles) ->
                val chunks = articles.chunked(GeminiSummarizer.BATCH_SIZE)
                chunks.forEach { chunk ->
                    val rendered = summarizer.summarize(
                        chunk.map {
                            SummaryInput(
                                id = it.id,
                                title = it.title,
                                description = it.description,
                                targetLanguage = targetLanguage,
                            )
                        }
                    )
                    // The fallback can't translate, so only keep what it
                    // produced when the article is already in this language.
                    chunk.forEach articleLoop@{ article ->
                        val text = rendered[article.id]
                        if (text == null) {
                            failedCount++
                            return@articleLoop
                        }
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
                                TextWriteResult.RECORDED_UNSERVED -> {
                                    failedCount++
                                    log.info("Recorded unserved text attempt for article ${article.id} in $targetLanguage")
                                }

                                TextWriteResult.RETAINED_AI -> renderedCount++

                                TextWriteResult.INSERTED_AI,
                                TextWriteResult.INSERTED_FALLBACK,
                                TextWriteResult.UPDATED_FALLBACK,
                                TextWriteResult.UPGRADED_TO_AI -> failedCount++
                            }
                            return@articleLoop
                        }
                        when (store.putText(article.id, targetLanguage, text.title, text.summary, text.source)) {
                            TextWriteResult.INSERTED_FALLBACK, TextWriteResult.UPDATED_FALLBACK ->
                                log.info("Stored fallback text for article ${article.id} in $targetLanguage after a retryable attempt")

                            TextWriteResult.UPGRADED_TO_AI ->
                                log.info("Upgraded fallback text for article ${article.id} in $targetLanguage")

                            TextWriteResult.RECORDED_UNSERVED -> {
                                failedCount++
                                return@articleLoop
                            }

                            TextWriteResult.INSERTED_AI,
                            TextWriteResult.RETAINED_AI -> Unit
                        }
                        // Classifying in the language the article was written
                        // in is the one that reads the original wording; the
                        // translation is a copy of a copy. A miss here is not
                        // recorded as an attempt — the article now has text, so
                        // classifyPending picks it up with its own retry budget.
                        val category = text.category?.takeIf {
                            text.source == TextSource.AI && article.sourceLanguage == targetLanguage
                        }
                        if (category != null) {
                            store.recordClassificationAttempt(article.id, category)
                        }
                        renderedCount++
                    }
                    delay(renderDelay)
                }
            }
        return TextRenderReport(rendered = renderedCount, failed = failedCount)
    }

    /**
     * Files the articles that already have text but no category of their own.
     *
     * These are the ones a fresh classification cannot reach for free: they were
     * summarized before the classifier existed, or the summarizer's own answer
     * came back unusable. Sending them back through summarization would rewrite
     * text that is already good, so they get their own request — one word an
     * article, [GeminiClassifier.BATCH_SIZE] at a time.
     */
    internal suspend fun classifyPending(): Int {
        if (classifier === NoClassifier) return 0
        val pending = store.pendingClassifications(maxClassificationsPerCycle)
        if (pending.isEmpty()) return 0
        log.info("Classifying ${pending.size} already-rendered articles")
        var classified = 0

        pending.chunked(GeminiClassifier.BATCH_SIZE).forEach { chunk ->
            val answers = classifier.classify(
                chunk.map { ClassifyInput(id = it.id, title = it.title, description = it.description) }
            )
            chunk.forEach { article ->
                val category = answers[article.id]
                store.recordClassificationAttempt(article.id, category)
                if (category != null) classified++
            }
            delay(renderDelay)
        }
        if (classified < pending.size) {
            log.warn("Classification failed for ${pending.size - classified} of ${pending.size} articles")
        }
        return classified
    }

    private companion object {
        const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
}
