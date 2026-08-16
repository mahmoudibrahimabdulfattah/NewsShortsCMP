package com.mk.newsshorts.domain.feed

import com.mk.newsshorts.domain.model.ArticleAuthor
import com.mk.newsshorts.domain.model.ArticleContent
import com.mk.newsshorts.domain.model.ArticleDescription
import com.mk.newsshorts.domain.model.ArticleId
import com.mk.newsshorts.domain.model.ArticleTitle
import com.mk.newsshorts.domain.model.ArticleUrl
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.model.NewsSource
import com.mk.newsshorts.domain.model.PublishedTimestamp
import com.mk.newsshorts.domain.model.SourceId
import com.mk.newsshorts.domain.model.SourceName
import com.mk.newsshorts.domain.ranking.deprioritiseSeen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedPagingTest {

    private fun article(url: String) = NewsArticle(
        id = ArticleId(url),
        title = ArticleTitle(url),
        description = ArticleDescription(""),
        content = ArticleContent(""),
        author = ArticleAuthor("Unknown"),
        source = NewsSource(id = SourceId("bbc"), name = SourceName("BBC News")),
        imageUrl = null,
        articleUrl = ArticleUrl(url),
        publishedAt = PublishedTimestamp(0L),
        category = NewsCategory.GENERAL,
    )

    private fun page(vararg urls: String) = urls.map(::article)

    // ---- when the next page is asked for ----

    @Test
    fun `a page is fetched before the reader reaches the end`() {
        assertTrue(
            shouldLoadNextPage(
                currentIndex = 40 - PREFETCH_DISTANCE,
                loadedCount = 40,
                hasNextPage = true,
                isLoading = false,
                failed = false,
            )
        )
    }

    @Test
    fun `a reader at the top of a page pulls nothing`() {
        assertFalse(
            shouldLoadNextPage(
                currentIndex = 0,
                loadedCount = 40,
                hasNextPage = true,
                isLoading = false,
                failed = false,
            )
        )
    }

    @Test
    fun `the end of the feed asks for nothing`() {
        assertFalse(
            shouldLoadNextPage(
                currentIndex = 39,
                loadedCount = 40,
                hasNextPage = false,
                isLoading = false,
                failed = false,
            )
        )
    }

    @Test
    fun `a page already in flight is not asked for twice`() {
        assertFalse(
            shouldLoadNextPage(
                currentIndex = 39,
                loadedCount = 40,
                hasNextPage = true,
                isLoading = true,
                failed = false,
            )
        )
    }

    @Test
    fun `an empty feed asks for nothing`() {
        assertFalse(
            shouldLoadNextPage(
                currentIndex = 0,
                loadedCount = 0,
                hasNextPage = true,
                isLoading = false,
                failed = false,
            )
        )
    }

    @Test
    fun `a failed page is not retried on every swipe`() {
        // Idling five cards from the end of a feed whose next page just failed
        // would otherwise re-request it on every card change.
        assertFalse(
            shouldLoadNextPage(
                currentIndex = 40 - PREFETCH_DISTANCE,
                loadedCount = 40,
                hasNextPage = true,
                isLoading = false,
                failed = true,
            )
        )
    }

    @Test
    fun `reaching the last card retries a failed page`() {
        assertTrue(
            shouldLoadNextPage(
                currentIndex = 39,
                loadedCount = 40,
                hasNextPage = true,
                isLoading = false,
                failed = true,
            )
        )
    }

    // ---- what happens to the list when a page lands ----

    @Test
    fun `a page is appended in order`() {
        val current = page("a", "b", "c")

        val grown = appendPage(current, page("d", "e"))

        assertEquals(listOf("a", "b", "c", "d", "e"), grown.map { it.articleUrl.value })
    }

    @Test
    fun `nothing already on screen is moved`() {
        val current = page("a", "b", "c")

        val grown = appendPage(current, page("d", "e"))

        assertEquals(current, grown.take(current.size))
    }

    @Test
    fun `an article already on screen is not appended again`() {
        val current = page("a", "b", "c")

        val grown = appendPage(current, page("c", "d"))

        assertEquals(listOf("a", "b", "c", "d"), grown.map { it.articleUrl.value })
    }

    @Test
    fun `a page that is entirely old news leaves the feed alone`() {
        val current = page("a", "b", "c")

        assertEquals(current, appendPage(current, page("a", "b")))
    }

    @Test
    fun `an empty page leaves the feed alone`() {
        val current = page("a", "b")

        assertEquals(current, appendPage(current, emptyList()))
    }

    @Test
    fun `a page repeating itself lands once`() {
        val grown = appendPage(page("a"), page("b", "b"))

        assertEquals(listOf("a", "b"), grown.map { it.articleUrl.value })
    }

    @Test
    fun `ranking a new page never reorders the cards above it`() {
        // The whole reason ranking is applied per page: `deprioritiseSeen` over
        // the joined list would push a card the reader is mid-swipe on down the
        // feed, because they have just read it.
        val current = page("a", "b", "c")
        val seen = setOf("a".hashCode(), "d".hashCode())
        val incoming = page("d", "e").deprioritiseSeen(seen)

        val grown = appendPage(current, incoming)

        assertEquals(listOf("a", "b", "c", "e", "d"), grown.map { it.articleUrl.value })
    }
}
