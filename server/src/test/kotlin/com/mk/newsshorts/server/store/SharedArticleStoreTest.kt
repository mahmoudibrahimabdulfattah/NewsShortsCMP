package com.mk.newsshorts.server.store

import com.mk.newsshorts.server.share.SharedArticle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The archive is what makes a shared link outlive the feed it came from, and it
 * is only ever written by a fresh process against a database CI restored from
 * the previous run — so every case here is really about what survives that.
 */
class SharedArticleStoreTest {

    private fun store(): ArticleStore {
        val db = File.createTempFile("shared-articles", ".db").apply { deleteOnExit() }
        return ArticleStore(db.absolutePath)
    }

    private fun article(
        slug: String,
        language: String = "en",
        summary: String = "A short summary.",
        publishedAt: Long = 1_700_000_000_000L,
    ) = SharedArticle(
        slug = slug,
        language = language,
        title = "Headline $slug",
        summary = summary,
        url = "https://example.com/$slug",
        imageUrl = "https://example.com/$slug.jpg",
        sourceName = "Example News",
        category = "general",
        publishedAt = publishedAt,
    )

    @Test
    fun `stores every field a page renders`() {
        val store = store()
        val subject = article("abc")

        store.archiveShared(listOf(subject))

        assertEquals(listOf(subject), store.sharedArticles(limit = 10))
    }

    /**
     * The same story in two languages is two pages, and they say different
     * things — so the slug alone cannot be the key.
     */
    @Test
    fun `keeps one row per language`() {
        val store = store()

        store.archiveShared(listOf(article("abc", language = "en"), article("abc", language = "ar")))

        assertEquals(2, store.sharedArticles(limit = 10).size)
    }

    /**
     * An article can reach a feed before the summarizer gets to it, carrying the
     * trimmed description as a stand-in. Insert-only would keep the stand-in on
     * the page for as long as the link lives.
     */
    @Test
    fun `a later run replaces a stand-in summary`() {
        val store = store()
        store.archiveShared(listOf(article("abc", summary = "Trimmed description…")))

        store.archiveShared(listOf(article("abc", summary = "The real summary.")))

        val stored = store.sharedArticles(limit = 10).single()
        assertEquals("The real summary.", stored.summary)
    }

    /** Newest first: a link's chance of being opened falls off with age. */
    @Test
    fun `returns the newest articles within the limit`() {
        val store = store()
        store.archiveShared(
            (1..5).map { article("s$it", publishedAt = 1_000L * it) }
        )

        val kept = store.sharedArticles(limit = 3)

        assertEquals(listOf("s5", "s4", "s3"), kept.map { it.slug })
    }

    @Test
    fun `pruning drops only what is older than the cutoff`() {
        val store = store()
        store.archiveShared(
            listOf(
                article("old", publishedAt = 1_000L),
                article("edge", publishedAt = 2_000L),
                article("new", publishedAt = 3_000L),
            )
        )

        val dropped = store.pruneShared(cutoffMillis = 2_000L)

        assertEquals(1, dropped)
        assertEquals(listOf("new", "edge"), store.sharedArticles(limit = 10).map { it.slug })
    }

    /**
     * The feed is pruned to a week; the archive is not. If pruning the feed
     * reached these rows, every link older than the feed would break — which is
     * the entire thing this table exists to prevent.
     */
    @Test
    fun `pruning the feed leaves the archive alone`() {
        val store = store()
        store.archiveShared(listOf(article("abc", publishedAt = 1_000L)))

        store.prune(cutoffMillis = 2_000L)

        assertTrue(store.sharedArticles(limit = 10).isNotEmpty())
    }
}
