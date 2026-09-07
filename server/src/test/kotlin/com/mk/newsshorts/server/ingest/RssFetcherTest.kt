package com.mk.newsshorts.server.ingest

import com.mk.newsshorts.server.model.FeedSource
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndEntryImpl
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RssFetcherTest {

    private val source = FeedSource(
        name = "Example",
        url = "https://example.com/rss.xml",
        language = "en",
        category = "general",
    )

    @Test
    fun `drops undated entries and records timestamp source`() {
        val publishedAt = 1_700_000_000_000L
        val updatedAt = 1_700_000_100_000L

        val snapshot = RssFetcher().toSnapshot(
            source = source,
            entries = listOf(
                entry(
                    title = "Published story",
                    url = "https://example.com/published",
                    publishedAt = publishedAt,
                    updatedAt = updatedAt,
                ),
                entry(
                    title = "Updated story",
                    url = "https://example.com/updated",
                    updatedAt = updatedAt,
                ),
                entry(
                    title = "Undated story",
                    url = "https://example.com/undated",
                ),
            ),
            effectiveUrl = source.url,
        )

        assertEquals(1, snapshot.undatedArticlesRejected)
        assertEquals(listOf("Published story", "Updated story"), snapshot.articles.map { it.title })

        val published = snapshot.articles.single { it.url.endsWith("/published") }
        assertEquals(publishedAt, published.publishedAtMillis)
        assertTrue(published.publishedAtIsPublication)

        val updated = snapshot.articles.single { it.url.endsWith("/updated") }
        assertEquals(updatedAt, updated.publishedAtMillis)
        assertFalse(updated.publishedAtIsPublication)
    }

    private fun entry(
        title: String,
        url: String,
        publishedAt: Long? = null,
        updatedAt: Long? = null,
    ): SyndEntry = SyndEntryImpl().apply {
        this.title = title
        link = url
        publishedAt?.let { publishedDate = Date(it) }
        updatedAt?.let { updatedDate = Date(it) }
        description = SyndContentImpl().apply {
            value = "Description"
        }
    }
}
