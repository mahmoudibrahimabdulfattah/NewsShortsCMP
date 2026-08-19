package com.mk.newsshorts.server.store

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The inbox is published from this history, so what it can answer is what a
 * reader can be shown. [PushLog] cannot answer any of it — that table keeps one
 * row per topic and rewrites it on every send.
 */
class PushHistoryStoreTest {

    private fun store(): ArticleStore {
        val db = File.createTempFile("push-history", ".db").apply { deleteOnExit() }
        return ArticleStore(db.absolutePath)
    }

    private fun ArticleStore.record(
        sentAt: Long,
        language: String = "ar",
        tier: String = "breaking",
        title: String = "Headline",
        deepLink: String? = "newsshorts://article?url=https%3A%2F%2Fexample.com%2Fa&title=x",
    ) = recordPushHistory(
        topic = "news_$language",
        language = language,
        tier = tier,
        title = title,
        body = "Body",
        deepLink = deepLink,
        sentAt = sentAt,
    )

    @Test
    fun `keeps every send rather than only the last`() {
        val store = store()

        store.record(sentAt = 1_000L, title = "First")
        store.record(sentAt = 2_000L, title = "Second")

        assertEquals(listOf("Second", "First"), store.pushHistory("ar", limit = 10).map { it.title })
    }

    /** The pacing record is a different question and stays one row per topic. */
    @Test
    fun `does not disturb the pacing record`() {
        val store = store()
        store.recordPush("news_ar", "https://example.com/a", 1_000L)

        store.record(sentAt = 2_000L)

        assertEquals(1_000L, store.lastPushAt("news_ar"))
    }

    @Test
    fun `separates the languages`() {
        val store = store()
        store.record(sentAt = 1_000L, language = "ar", title = "عنوان")
        store.record(sentAt = 1_000L, language = "en", title = "Headline")

        assertEquals(listOf("عنوان"), store.pushHistory("ar", limit = 10).map { it.title })
        assertEquals(listOf("Headline"), store.pushHistory("en", limit = 10).map { it.title })
    }

    /**
     * A reminder has no article behind it, so an inbox row for one would open
     * nothing — tapped once, and the list is not trusted again.
     */
    @Test
    fun `leaves out sends with no article`() {
        val store = store()
        store.record(sentAt = 1_000L, tier = "breaking", title = "Story")
        store.record(sentAt = 2_000L, tier = "reminder", title = "Nudge", deepLink = null)

        assertEquals(listOf("Story"), store.pushHistory("ar", limit = 10).map { it.title })
    }

    @Test
    fun `pruning drops only what is older than the cutoff`() {
        val store = store()
        store.record(sentAt = 1_000L, title = "Old")
        store.record(sentAt = 2_000L, title = "Edge")
        store.record(sentAt = 3_000L, title = "New")

        val dropped = store.prunePushHistory(cutoffMillis = 2_000L)

        assertEquals(1, dropped)
        assertEquals(listOf("New", "Edge"), store.pushHistory("ar", limit = 10).map { it.title })
    }

    /**
     * Every publish is a fresh process against a database CI restored from the
     * last run, and a resend of the same slot must not double the row.
     */
    @Test
    fun `recording the same send twice changes nothing`() {
        val store = store()
        store.record(sentAt = 1_000L)

        store.record(sentAt = 1_000L)

        assertEquals(1, store.pushHistory("ar", limit = 10).size)
    }

    @Test
    fun `caps the list at the limit, newest first`() {
        val store = store()
        (1..5).forEach { store.record(sentAt = 1_000L * it, title = "n$it") }

        assertEquals(listOf("n5", "n4", "n3"), store.pushHistory("ar", limit = 3).map { it.title })
        assertTrue(store.pushHistory("ar", limit = 100).size == 5)
    }
}
