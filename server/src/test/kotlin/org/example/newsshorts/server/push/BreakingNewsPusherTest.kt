package org.example.newsshorts.server.push

import kotlinx.coroutines.runBlocking
import org.example.newsshorts.server.model.FeedArticleDto
import org.example.newsshorts.server.store.ArticleStore
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The pacing rules decide how often a reader is interrupted, and they depend on
 * elapsed time, so a single pipeline run cannot show whether they hold.
 */
class BreakingNewsPusherTest {

    private val dbFile = File.createTempFile("push-test", ".db").apply { delete() }
    private val store = ArticleStore(dbFile.absolutePath)
    private val now = 1_800_000_000_000L

    @AfterTest
    fun cleanUp() {
        dbFile.delete()
    }

    private class RecordingNotifier(private val succeeds: Boolean = true) : Notifier {
        val sent = mutableListOf<Pair<String, FeedArticleDto>>()
        override suspend fun send(topic: String, article: FeedArticleDto): Boolean {
            sent += topic to article
            return succeeds
        }
    }

    private fun seedArticle(language: String, publishedAt: Long, url: String = "https://example.com/$language") {
        val id = store.insertIfNew(
            title = "Headline", url = url, description = "Body", imageUrl = null,
            sourceName = "Source", language = language, category = "general",
            country = null, publishedAt = publishedAt,
        ) ?: return
        store.putText(id, language, "Headline", "Summary")
    }

    @Test
    fun `sends the freshest general story for each language`() = runBlocking {
        seedArticle("en", now - 30.minutes.inWholeMilliseconds)
        seedArticle("ar", now - 30.minutes.inWholeMilliseconds)
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)

        assertEquals(setOf("news_en", "news_ar"), notifier.sent.map { it.first }.toSet())
    }

    @Test
    fun `stays quiet until the gap has passed`() = runBlocking {
        seedArticle("en", now - 30.minutes.inWholeMilliseconds)
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)
        val afterFirst = notifier.sent.size
        // Same run again an hour later: still inside the six-hour gap.
        BreakingNewsPusher(store, notifier).run(now + 1.hours.inWholeMilliseconds)

        assertEquals(afterFirst, notifier.sent.size, "a second push arrived inside the quiet window")
        assertTrue(afterFirst > 0)
    }

    @Test
    fun `sends again once the gap has passed`() = runBlocking {
        seedArticle("en", now - 30.minutes.inWholeMilliseconds)
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)
        val later = now + 7.hours.inWholeMilliseconds
        seedArticle("en", later - 10.minutes.inWholeMilliseconds, url = "https://example.com/en-2")
        BreakingNewsPusher(store, notifier).run(later)

        assertEquals(2, notifier.sent.count { it.first == "news_en" })
    }

    @Test
    fun `ignores stories that are no longer breaking`() = runBlocking {
        seedArticle("en", now - 10.hours.inWholeMilliseconds)
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)

        assertTrue(notifier.sent.isEmpty(), "an old story was pushed as breaking news")
    }

    @Test
    fun `a failed delivery is retried on the next run`() = runBlocking {
        seedArticle("en", now - 30.minutes.inWholeMilliseconds)
        val failing = RecordingNotifier(succeeds = false)

        BreakingNewsPusher(store, failing).run(now)
        // Nothing was recorded, so the very next run may try again.
        BreakingNewsPusher(store, failing).run(now + 1.minutes.inWholeMilliseconds)

        assertEquals(2, failing.sent.size)
    }
}
