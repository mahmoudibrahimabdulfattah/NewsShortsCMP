package com.mk.newsshorts.server.ingest

import com.mk.newsshorts.server.model.FeedSource
import com.rometools.rome.feed.module.DCModule
import com.rometools.rome.feed.rss.Item
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndEntryImpl
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun `captures supplied author creator and rights`() {
        val publishedAt = 1_700_000_000_000L

        val snapshot = RssFetcher().toSnapshot(
            source = source,
            entries = listOf(
                entry(
                    title = "Author story",
                    url = "https://example.com/author",
                    publishedAt = publishedAt,
                    author = "Jane Reporter",
                    creator = "Ignored Creator",
                    rights = "Copyright Example",
                ),
                entry(
                    title = "Creator story",
                    url = "https://example.com/creator",
                    publishedAt = publishedAt,
                    creator = "Dana Creator",
                    rights = "CC BY 4.0",
                ),
            ),
            effectiveUrl = source.url,
        )

        val authorStory = snapshot.articles.single { it.url.endsWith("/author") }
        assertEquals("Jane Reporter", authorStory.author)
        assertEquals("Copyright Example", authorStory.rightsNotice)

        val creatorStory = snapshot.articles.single { it.url.endsWith("/creator") }
        assertEquals("Dana Creator", creatorStory.author)
        assertEquals("CC BY 4.0", creatorStory.rightsNotice)
    }

    @Test
    fun `absent author stays absent`() {
        val snapshot = RssFetcher().toSnapshot(
            source = source,
            entries = listOf(
                entry(
                    title = "No byline",
                    url = "https://example.com/no-byline",
                    publishedAt = 1_700_000_000_000L,
                ),
            ),
            effectiveUrl = source.url,
        )

        assertNull(snapshot.articles.single().author)
    }

    private fun entry(
        title: String,
        url: String,
        publishedAt: Long? = null,
        updatedAt: Long? = null,
        author: String? = null,
        creator: String? = null,
        rights: String? = null,
    ): SyndEntry = SyndEntryImpl().apply {
        this.title = title
        link = url
        publishedAt?.let { publishedDate = Date(it) }
        updatedAt?.let { updatedDate = Date(it) }
        description = SyndContentImpl().apply {
            value = "Description"
        }
        val dc = getModule(DCModule.URI) as DCModule
        creator?.let { dc.creator = it }
        rights?.let { dc.rights = it }
        author?.let {
            // SyndEntry.author is backed by the DC module; use the preserved
            // RSS wire item to model a native <author> alongside dc:creator.
            wireEntry = Item().apply { this.author = it }
            if (!dc.creators.contains(it)) {
                dc.creators.add(it)
            }
        }
    }
}
