package com.mk.newsshorts.server

import com.mk.newsshorts.server.model.FeedArticleDto
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PublishHealth(
    val generatedAt: Long,
    val sourcesTotal: Int,
    val sourcesEmpty: Int,
    val articlesInserted: Int,
    val textsRendered: Int,
    val textsFailed: Int,
    val feedArticles: Map<String, Int>,
    val newestArticleAt: Map<String, Long?>,
    val sourcesRejected: List<String> = emptyList(),
    val categoryFeedArticles: Map<String, Int> = emptyMap(),
    val newestCategoryArticleAt: Map<String, Long?> = emptyMap(),
    val categoryGuardsReady: Set<String> = emptySet(),
    val guardsDisabled: Boolean = false,
    val warningChecks: List<String> = emptyList(),
    val failedChecks: List<String> = emptyList(),
)

class PublishHealthFailure(val failedChecks: List<String>) :
    IllegalStateException("Publish health failed: ${failedChecks.joinToString()}")

object PublishHealthChecks {
    const val ALL_SOURCES_EMPTY = "all_sources_empty"
    const val ALL_RENDERS_FAILED = "all_renders_failed"

    fun emptyLanguage(language: String) = "language_feed_empty:$language"
    fun staleLanguage(language: String) = "language_feed_stale:$language"
    fun thinCategory(feedKey: String) = "category_feed_thin:$feedKey"
    fun staleCategory(feedKey: String) = "category_feed_stale:$feedKey"
}

/**
 * Decides only from the report it is handed, so production and tests apply the
 * same thresholds without fetching, opening a database, or consulting process
 * configuration.
 */
fun evaluate(
    health: PublishHealth,
    now: Long,
    staleHours: Long,
    minCategoryArticles: Int = DEFAULT_MIN_CATEGORY_ARTICLES,
): List<String> = evaluateChecks(health, now, staleHours, minCategoryArticles).failed

internal fun evaluateWarnings(
    health: PublishHealth,
    now: Long,
    staleHours: Long,
    minCategoryArticles: Int = DEFAULT_MIN_CATEGORY_ARTICLES,
): List<String> = evaluateChecks(health, now, staleHours, minCategoryArticles).warnings

private data class HealthChecks(val failed: List<String>, val warnings: List<String>)

private fun evaluateChecks(
    health: PublishHealth,
    now: Long,
    staleHours: Long,
    minCategoryArticles: Int,
): HealthChecks {
    require(staleHours > 0 && staleHours <= Long.MAX_VALUE / MILLIS_PER_HOUR) {
        "staleHours must be a positive duration"
    }
    val staleMillis = staleHours * MILLIS_PER_HOUR
    require(minCategoryArticles > 0) { "minCategoryArticles must be positive" }
    val failed = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    // Counted over every source fetched, rejected ones included: a rejected
    // section still hands over its articles, so it is only silent when it
    // returned nothing — which is exactly what sourcesEmpty already records.
    if (health.sourcesEmpty >= health.sourcesTotal) {
        failed += PublishHealthChecks.ALL_SOURCES_EMPTY
    }

    health.feedArticles.toSortedMap().forEach { (language, count) ->
        if (count == 0) failed += PublishHealthChecks.emptyLanguage(language)

        val newest = health.newestArticleAt[language]
        if (newest == null || now - newest > staleMillis) {
            failed += PublishHealthChecks.staleLanguage(language)
        }
    }

    health.categoryFeedArticles.toSortedMap().forEach { (feedKey, count) ->
        val destination = if (feedKey in health.categoryGuardsReady) failed else warnings
        if (count < minCategoryArticles) {
            destination += PublishHealthChecks.thinCategory(feedKey)
        }
        val newest = health.newestCategoryArticleAt[feedKey]
        if (newest == null || now - newest > staleMillis) {
            destination += PublishHealthChecks.staleCategory(feedKey)
        }
    }

    if (health.textsRendered == 0 && health.textsFailed >= MIN_FAILED_RENDERS) {
        failed += PublishHealthChecks.ALL_RENDERS_FAILED
    }

    return HealthChecks(failed = failed, warnings = warnings)
}

/** A future-dated publisher cannot make a frozen feed look healthy forever. */
internal fun newestPlausibleArticleAt(articles: List<FeedArticleDto>, now: Long): Long? {
    val latestAllowed = if (now > Long.MAX_VALUE - FUTURE_ARTICLE_TOLERANCE_MILLIS) {
        Long.MAX_VALUE
    } else {
        now + FUTURE_ARTICLE_TOLERANCE_MILLIS
    }
    return articles.asSequence()
        .map { it.publishedAt }
        .filter { it <= latestAllowed }
        .maxOrNull()
}

/**
 * Persists the evidence before rejecting the run, so the alert job can report
 * the measured failure even though Pages keeps serving the previous publish.
 */
internal fun writePublishHealth(
    outputDir: File,
    health: PublishHealth,
    now: Long,
    staleHours: Long,
    minCategoryArticles: Int = DEFAULT_MIN_CATEGORY_ARTICLES,
): PublishHealth {
    val checks = evaluateChecks(health, now, staleHours, minCategoryArticles)
    val measured = health.copy(
        warningChecks = checks.warnings,
        failedChecks = checks.failed,
    )
    File(outputDir, "v1/health.json")
        .apply { parentFile.mkdirs() }
        .writeText(HEALTH_JSON.encodeToString(measured))

    if (!measured.guardsDisabled && measured.failedChecks.isNotEmpty()) {
        throw PublishHealthFailure(measured.failedChecks)
    }
    return measured
}

private val HEALTH_JSON = Json { encodeDefaults = true }
private const val MIN_FAILED_RENDERS = 20
const val DEFAULT_MIN_CATEGORY_ARTICLES = 40
private const val MILLIS_PER_HOUR = 60L * 60 * 1000
private const val FUTURE_ARTICLE_TOLERANCE_MILLIS = MILLIS_PER_HOUR
