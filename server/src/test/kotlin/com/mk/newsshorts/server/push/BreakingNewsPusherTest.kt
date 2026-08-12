package com.mk.newsshorts.server.push

import kotlinx.coroutines.runBlocking
import com.mk.newsshorts.server.model.FeedArticleDto
import com.mk.newsshorts.server.store.ArticleStore
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        val sent = mutableListOf<Pair<String, PushMessage>>()
        override suspend fun send(topic: String, message: PushMessage): Boolean {
            sent += topic to message
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
    fun `a story past the breaking window still goes out, as a top story`() = runBlocking {
        seedArticle("en", now - 10.hours.inWholeMilliseconds)
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)

        // The slot is used either way; what changes is the label, so a reader
        // is not told a ten-hour-old story just broke.
        val message = notifier.sent.first { it.first == "news_en" }.second
        assertEquals(PushTier.TOP_STORY, message.tier)
        assertEquals("Headline", message.title)
    }

    @Test
    fun `a fresh story is labelled breaking`() = runBlocking {
        seedArticle("en", now - 30.minutes.inWholeMilliseconds)
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)

        assertEquals(PushTier.BREAKING, notifier.sent.first { it.first == "news_en" }.second.tier)
    }

    @Test
    fun `an empty feed still fills the slot, with a reminder`() = runBlocking {
        // Nothing seeded: no article exists in any language.
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)

        val message = notifier.sent.first { it.first == "news_ar" }.second
        assertEquals(PushTier.REMINDER, message.tier)
        // No article behind it, so tapping opens the feed rather than a story.
        assertNull(message.deepLink)
        assertTrue(message.title.isNotBlank() && message.body.isNotBlank())
    }

    @Test
    fun `a stale feed falls back to a reminder rather than a week-old headline`() = runBlocking {
        seedArticle("en", now - 40.hours.inWholeMilliseconds)
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)

        assertEquals(PushTier.REMINDER, notifier.sent.first { it.first == "news_en" }.second.tier)
    }

    @Test
    fun `reminder wording changes between days`() {
        val monday = ReminderCopy.forLanguage("ar", 20_000)
        val tuesday = ReminderCopy.forLanguage("ar", 20_001)
        assertTrue(monday != tuesday, "the same line repeated on consecutive days")
        // And it is Arabic for the Arabic topic, not a default.
        assertTrue(monday.first != ReminderCopy.forLanguage("en", 20_000).first)
    }

    @Test
    fun `a failed delivery is retried on the next run`() = runBlocking {
        seedArticle("en", now - 30.minutes.inWholeMilliseconds)
        val failing = RecordingNotifier(succeeds = false)

        BreakingNewsPusher(store, failing).run(now)
        // Nothing was recorded, so the very next run may try again.
        BreakingNewsPusher(store, failing).run(now + 1.minutes.inWholeMilliseconds)

        // Counted per topic: every language now fills its slot, so the total
        // includes the Arabic reminder as well.
        assertEquals(2, failing.sent.count { it.first == "news_en" })
    }
}
