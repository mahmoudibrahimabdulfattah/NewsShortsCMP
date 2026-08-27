package com.mk.newsshorts.server.push

import kotlinx.coroutines.runBlocking
import com.mk.newsshorts.server.model.FeedArticleDto
import com.mk.newsshorts.server.store.ArticleStore
import com.mk.newsshorts.server.store.TextSource
import com.mk.newsshorts.server.store.TextWriteResult
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
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

    private fun seedArticle(
        language: String,
        publishedAt: Long,
        url: String = "https://example.com/$language",
        textSource: TextSource = TextSource.AI,
        title: String = "Headline",
        summary: String = "Summary",
    ): Long {
        val id = store.insertIfNew(
            title = "Headline", url = url, description = "Body", imageUrl = null,
            sourceName = "Source", language = language, category = "general",
            country = null, publishedAt = publishedAt,
        ) ?: error("article already existed: $url")
        store.putText(id, language, title, summary, textSource)
        return id
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

    /** What the in-app inbox is published from, so an unrecorded send is a gap. */
    @Test
    fun `a delivered notification joins the history`() = runBlocking {
        seedArticle("en", now - 30.minutes.inWholeMilliseconds)

        BreakingNewsPusher(store, RecordingNotifier()).run(now)

        val sent = store.pushHistory("en", limit = 10).single()
        assertEquals("Headline", sent.title)
        assertEquals("breaking", sent.tier)
        assertTrue(sent.deepLink.startsWith("newsshorts://article?"), sent.deepLink)
        assertEquals(now, sent.sentAt)
    }

    /**
     * The same rule the pacing record follows: a send that did not happen must
     * leave nothing behind, or the inbox lists a notification nobody received.
     */
    @Test
    fun `a failed delivery leaves no history`() = runBlocking {
        seedArticle("en", now - 30.minutes.inWholeMilliseconds)

        BreakingNewsPusher(store, RecordingNotifier(succeeds = false)).run(now)

        assertTrue(store.pushHistory("en", limit = 10).isEmpty())
    }

    /** A reminder has no article, so it is sent but never listed. */
    @Test
    fun `a reminder is delivered but stays out of the inbox`() = runBlocking {
        // Nothing seeded, so no language has a story and both fall to a reminder.
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)

        assertTrue(notifier.sent.all { it.second.tier == PushTier.REMINDER })
        assertTrue(store.pushHistory("ar", limit = 10).isEmpty())
        assertTrue(store.pushHistory("en", limit = 10).isEmpty())
    }

    @Test
    fun `a quiet feed does not repeat the same story after the gap`() = runBlocking {
        seedArticle("en", now - 30.minutes.inWholeMilliseconds)
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)
        BreakingNewsPusher(store, notifier).run(now + 7.hours.inWholeMilliseconds)

        val messages = notifier.sent.filter { it.first == "news_en" }.map { it.second }
        assertEquals(2, messages.size)
        assertEquals(PushTier.REMINDER, messages.last().tier)
        assertNull(messages.last().deepLink)
    }

    @Test
    fun `a story sent before inbox history is not repeated after the gap`() = runBlocking {
        seedArticle("en", now - 8.hours.inWholeMilliseconds)
        val article = store.feed("en", "general", limit = 1, offset = 0).first.single()
        store.recordPush(
            topic = "news_en",
            articleUrl = ArticleDeepLinks.build(article),
            sentAt = now - 7.hours.inWholeMilliseconds,
        )
        assertTrue(store.pushHistory("en", limit = 10).isEmpty())
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)

        val message = notifier.sent.single { it.first == "news_en" }.second
        assertEquals(PushTier.REMINDER, message.tier)
        assertNull(message.deepLink)
    }

    @Test
    fun `a newer story still reaches readers after an earlier send`() = runBlocking {
        val firstUrl = "https://example.com/en-1"
        val secondUrl = "https://example.com/en-2"
        seedArticle("en", now - 30.minutes.inWholeMilliseconds, url = firstUrl)
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)
        val later = now + 7.hours.inWholeMilliseconds
        seedArticle("en", later - 10.minutes.inWholeMilliseconds, url = secondUrl)
        BreakingNewsPusher(store, notifier).run(later)

        val messages = notifier.sent.filter { it.first == "news_en" }.map { it.second }
        assertEquals(firstUrl, ArticleDeepLinks.articleUrlOf(messages.first().deepLink!!))
        assertEquals(secondUrl, ArticleDeepLinks.articleUrlOf(messages.last().deepLink!!))
    }

    @Test
    fun `an older unsent story fills the next quiet slot`() = runBlocking {
        val olderUrl = "https://example.com/older"
        val newerUrl = "https://example.com/newer"
        seedArticle("en", now - 2.hours.inWholeMilliseconds, url = olderUrl)
        seedArticle("en", now - 30.minutes.inWholeMilliseconds, url = newerUrl)
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)
        BreakingNewsPusher(store, notifier).run(now + 7.hours.inWholeMilliseconds)

        val messages = notifier.sent.filter { it.first == "news_en" }.map { it.second }
        assertEquals(newerUrl, ArticleDeepLinks.articleUrlOf(messages.first().deepLink!!))
        assertEquals(olderUrl, ArticleDeepLinks.articleUrlOf(messages.last().deepLink!!))
        assertEquals(PushTier.TOP_STORY, messages.last().tier)
    }

    @Test
    fun `an exhausted feed sends a reminder instead of repeating a story`() = runBlocking {
        seedArticle("en", now - 2.hours.inWholeMilliseconds, url = "https://example.com/older")
        seedArticle("en", now - 30.minutes.inWholeMilliseconds, url = "https://example.com/newer")
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)
        BreakingNewsPusher(store, notifier).run(now + 7.hours.inWholeMilliseconds)
        BreakingNewsPusher(store, notifier).run(now + 14.hours.inWholeMilliseconds)

        val messages = notifier.sent.filter { it.first == "news_en" }.map { it.second }
        assertEquals(3, messages.size)
        assertEquals(PushTier.REMINDER, messages.last().tier)
        assertNull(messages.last().deepLink)
    }

    @Test
    fun `a failed story delivery is retried on the next cycle`() = runBlocking {
        val url = "https://example.com/retry"
        seedArticle("en", now - 30.minutes.inWholeMilliseconds, url = url)
        val failing = RecordingNotifier(succeeds = false)

        BreakingNewsPusher(store, failing).run(now)

        val succeeding = RecordingNotifier()
        BreakingNewsPusher(store, succeeding).run(now + 1.minutes.inWholeMilliseconds)

        val retry = succeeding.sent.single { it.first == "news_en" }.second
        assertEquals(url, ArticleDeepLinks.articleUrlOf(retry.deepLink!!))
        assertEquals(1, store.pushHistory("en", limit = 10).size)
    }

    @Test
    fun `consecutive quiet slots can both carry reminders`() = runBlocking {
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)
        BreakingNewsPusher(store, notifier).run(now + 7.hours.inWholeMilliseconds)

        val messages = notifier.sent.filter { it.first == "news_en" }.map { it.second }
        assertEquals(2, messages.size)
        assertTrue(messages.all { it.tier == PushTier.REMINDER && it.deepLink == null })
    }

    @Test
    fun `an upgraded summary does not make an old story new again`() = runBlocking {
        val url = "https://example.com/upgraded"
        val id = seedArticle(
            language = "en",
            publishedAt = now - 30.minutes.inWholeMilliseconds,
            url = url,
            textSource = TextSource.FALLBACK,
            summary = "Temporary summary",
        )
        val notifier = RecordingNotifier()
        val beforeArticle = store.feed("en", "general", limit = 1, offset = 0).first.single()
        val beforeLink = ArticleDeepLinks.build(beforeArticle)

        BreakingNewsPusher(store, notifier).run(now)
        val result = store.putText(id, "en", "AI headline", "AI summary", TextSource.AI)
        val afterArticle = store.feed("en", "general", limit = 1, offset = 0).first.single()
        val afterLink = ArticleDeepLinks.build(afterArticle)
        BreakingNewsPusher(store, notifier).run(now + 7.hours.inWholeMilliseconds)

        assertEquals(TextWriteResult.UPGRADED_TO_AI, result)
        assertNotEquals(beforeLink, afterLink)
        assertEquals(ArticleDeepLinks.articleUrlOf(beforeLink), ArticleDeepLinks.articleUrlOf(afterLink))
        val messages = notifier.sent.filter { it.first == "news_en" }.map { it.second }
        assertEquals(PushTier.REMINDER, messages.last().tier)
    }

    @Test
    fun `a story that becomes news again is not blocked forever`() = runBlocking {
        val url = "https://example.com/returns"
        seedArticle("en", now - 30.minutes.inWholeMilliseconds, url = url)
        val notifier = RecordingNotifier()

        BreakingNewsPusher(store, notifier).run(now)
        store.prune(now)
        val later = now + 7.days.inWholeMilliseconds + 1
        seedArticle("en", later - 10.minutes.inWholeMilliseconds, url = url)
        BreakingNewsPusher(store, notifier).run(later)

        val messages = notifier.sent.filter { it.first == "news_en" }.map { it.second }
        assertEquals(2, messages.size)
        assertEquals(url, ArticleDeepLinks.articleUrlOf(messages.last().deepLink!!))
    }
}
