package com.mk.newsshorts.server

import com.mk.newsshorts.server.model.FeedArticleDto
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublishHealthTest {

    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a run where every source is dark fails the publish`() {
        val failure = assertFailsWith<PublishHealthFailure> {
            write(tempDir(), healthyHealth().copy(sourcesEmpty = 58))
        }

        assertEquals(listOf(PublishHealthChecks.ALL_SOURCES_EMPTY), failure.failedChecks)
    }

    @Test
    fun `a rejected source is not a silent one`() {
        // Rejecting a section only voids its label; the feed still hands over
        // its articles, so a run of rejections is not a run that fetched
        // nothing and must not be failed as one.
        val health = write(
            tempDir(),
            healthyHealth().copy(
                sourcesTotal = 3,
                sourcesEmpty = 1,
                sourcesRejected = listOf("Broken sport", "Duplicate science"),
            ),
        )

        assertEquals(emptyList(), health.failedChecks)
    }

    @Test
    fun `a run where every source returned nothing fails`() {
        val failure = assertFailsWith<PublishHealthFailure> {
            write(tempDir(), healthyHealth().copy(sourcesTotal = 3, sourcesEmpty = 3))
        }

        assertTrue(PublishHealthChecks.ALL_SOURCES_EMPTY in failure.failedChecks)
    }

    @Test
    fun `a language with no articles fails the publish`() {
        val failure = assertFailsWith<PublishHealthFailure> {
            write(
                tempDir(),
                healthyHealth().copy(feedArticles = mapOf("ar" to 0, "en" to 3)),
            )
        }

        assertEquals(listOf(PublishHealthChecks.emptyLanguage("ar")), failure.failedChecks)
    }

    @Test
    fun `a feed nobody has updated for a day fails the publish`() {
        val atBoundary = healthyHealth().copy(
            newestArticleAt = mapOf("ar" to now - 24 * hour, "en" to now - hour),
        )
        assertTrue(write(tempDir(), atBoundary).failedChecks.isEmpty())

        val pastBoundary = atBoundary.copy(
            newestArticleAt = mapOf("ar" to now - 24 * hour - 1, "en" to now - hour),
        )
        val stale = assertFailsWith<PublishHealthFailure> { write(tempDir(), pastBoundary) }
        assertEquals(listOf(PublishHealthChecks.staleLanguage("ar")), stale.failedChecks)

        val missing = assertFailsWith<PublishHealthFailure> {
            write(tempDir(), atBoundary.copy(newestArticleAt = mapOf("ar" to null, "en" to now)))
        }
        assertEquals(listOf(PublishHealthChecks.staleLanguage("ar")), missing.failedChecks)

        val plausible = article(now - 25 * hour)
        val badFuture = article(now + hour + 1)
        assertEquals(
            plausible.publishedAt,
            newestPlausibleArticleAt(listOf(plausible, badFuture), now),
        )
        assertEquals(now + hour, newestPlausibleArticleAt(listOf(article(now + hour)), now))
    }

    @Test
    fun `every summary failing fails the publish`() {
        val failure = assertFailsWith<PublishHealthFailure> {
            write(tempDir(), healthyHealth().copy(textsRendered = 0, textsFailed = 20))
        }

        assertEquals(listOf(PublishHealthChecks.ALL_RENDERS_FAILED), failure.failedChecks)
    }

    @Test
    fun `a category with less than one page fails the publish`() {
        val health = healthyHealth().copy(
            categoryFeedArticles = mapOf("ar-sports" to 39),
            newestCategoryArticleAt = mapOf("ar-sports" to now - hour),
            categoryGuardsReady = setOf("ar-sports"),
        )

        val failure = assertFailsWith<PublishHealthFailure> { write(tempDir(), health) }

        assertEquals(listOf(PublishHealthChecks.thinCategory("ar-sports")), failure.failedChecks)
    }

    @Test
    fun `a full but stale category fails the publish`() {
        val health = healthyHealth().copy(
            categoryFeedArticles = mapOf("ar-science" to 40),
            newestCategoryArticleAt = mapOf("ar-science" to now - 24 * hour - 1),
            categoryGuardsReady = setOf("ar-science"),
        )

        val failure = assertFailsWith<PublishHealthFailure> { write(tempDir(), health) }

        assertEquals(listOf(PublishHealthChecks.staleCategory("ar-science")), failure.failedChecks)
    }

    @Test
    fun `a fresh category with one page publishes`() {
        val health = healthyHealth().copy(
            categoryFeedArticles = mapOf("ar-health" to 40),
            newestCategoryArticleAt = mapOf("ar-health" to now - hour),
        )

        assertTrue(write(tempDir(), health).failedChecks.isEmpty())
    }

    @Test
    fun `a category still warming up reports warnings without freezing publish`() {
        val health = healthyHealth().copy(
            categoryFeedArticles = mapOf("ar-sports" to 0),
            newestCategoryArticleAt = mapOf("ar-sports" to null),
        )

        val saved = write(tempDir(), health)

        assertTrue(saved.failedChecks.isEmpty())
        assertEquals(
            listOf(
                PublishHealthChecks.thinCategory("ar-sports"),
                PublishHealthChecks.staleCategory("ar-sports"),
            ),
            saved.warningChecks,
        )
    }

    @Test
    fun `the health file survives the failure that produced it`() {
        val outputDir = tempDir()
        assertFailsWith<PublishHealthFailure> {
            write(outputDir, healthyHealth().copy(sourcesEmpty = 58))
        }

        val saved = readHealth(outputDir)
        assertEquals(listOf(PublishHealthChecks.ALL_SOURCES_EMPTY), saved.failedChecks)
    }

    // Negative control: this passes with the guards deleted and is a control,
    // not regression proof.
    @Test
    fun `two quiet sources out of fifty-eight still publish`() {
        val saved = write(tempDir(), healthyHealth().copy(sourcesEmpty = 2))

        assertTrue(saved.failedChecks.isEmpty())
    }

    // Negative control: this passes with the guards deleted and is a control,
    // not regression proof.
    @Test
    fun `a feed updated an hour ago publishes normally`() {
        val saved = write(
            tempDir(),
            healthyHealth().copy(newestArticleAt = mapOf("ar" to now - hour, "en" to now - hour)),
        )

        assertTrue(saved.failedChecks.isEmpty())
    }

    // Negative control: this passes with the guards deleted and is a control,
    // not regression proof.
    @Test
    fun `a handful of failed summaries does not fail the publish`() {
        val saved = write(tempDir(), healthyHealth().copy(textsRendered = 0, textsFailed = 19))

        assertTrue(saved.failedChecks.isEmpty())
    }

    // Negative control: this passes with the guards deleted and is a control,
    // not regression proof.
    @Test
    fun `health json records what the run actually did`() {
        val outputDir = tempDir()
        val report = healthyHealth().copy(
            sourcesEmpty = 2,
            articlesInserted = 37,
            textsRendered = 29,
            textsFailed = 4,
            feedArticles = mapOf("ar" to 17, "en" to 23),
            newestArticleAt = mapOf("ar" to now - 2_000, "en" to now - 1_000),
        )

        write(outputDir, report)

        assertEquals(report, readHealth(outputDir))
    }

    // Negative control: this passes with the guards deleted and is a control,
    // not regression proof.
    @Test
    fun `guards can be turned off for one run`() {
        val outputDir = tempDir()
        val rejected = healthyHealth().copy(sourcesEmpty = 58)
        assertEquals(
            listOf(PublishHealthChecks.ALL_SOURCES_EMPTY),
            evaluate(rejected, now = now, staleHours = 24),
        )

        write(outputDir, rejected.copy(guardsDisabled = true))

        val saved = readHealth(outputDir)
        assertTrue(saved.guardsDisabled)
        assertFalse(saved.failedChecks.isEmpty())
    }

    private fun healthyHealth() = PublishHealth(
        generatedAt = now,
        sourcesTotal = 58,
        sourcesEmpty = 0,
        articlesInserted = 7,
        textsRendered = 21,
        textsFailed = 3,
        feedArticles = mapOf("ar" to 2, "en" to 3),
        newestArticleAt = mapOf("ar" to now - hour, "en" to now - hour),
    )

    private fun write(outputDir: File, health: PublishHealth): PublishHealth =
        writePublishHealth(outputDir, health, now = now, staleHours = 24)

    private fun readHealth(outputDir: File): PublishHealth =
        json.decodeFromString(File(outputDir, "v1/health.json").readText())

    private fun tempDir(): File = Files.createTempDirectory("publish-health").toFile().apply {
        deleteOnExit()
    }

    private fun article(publishedAt: Long) = FeedArticleDto(
        id = publishedAt,
        title = "Headline",
        summary = "Summary",
        url = "https://example.com/$publishedAt",
        imageUrl = null,
        sourceName = "Source",
        language = "en",
        category = "general",
        publishedAt = publishedAt,
    )
}
