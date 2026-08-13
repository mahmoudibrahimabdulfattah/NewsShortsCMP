package com.mk.newsshorts.domain.sync

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
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This runs the moment a reader signs in on a device that already has its own
 * bookmarks — the case that matters is that neither side's saves are ever
 * discarded, because a lost bookmark here is a lost bookmark the reader has no
 * way to notice, let alone recover.
 */
class SavedArticlesMergeTest {

    private fun article(id: String, url: String) = NewsArticle(
        id = ArticleId(id),
        title = ArticleTitle("Headline $id"),
        description = ArticleDescription(""),
        content = ArticleContent(""),
        author = ArticleAuthor(""),
        source = NewsSource(SourceId("s"), SourceName("Source")),
        imageUrl = null,
        articleUrl = ArticleUrl(url),
        publishedAt = PublishedTimestamp(0L),
        category = NewsCategory.GENERAL,
    )

    private val a = article("a", "https://example.com/a")
    private val b = article("b", "https://example.com/b")
    private val c = article("c", "https://example.com/c")

    @Test
    fun `disjoint sets union without dropping either side`() {
        val result = mergeSavedArticles(local = listOf(a), remote = listOf(b))
        assertEquals(setOf(a, b), result.toSet())
    }

    @Test
    fun `a duplicate url is kept once and it is the local copy`() {
        val remoteCopyOfA = article("a-remote", "https://example.com/a")
        val result = mergeSavedArticles(local = listOf(a), remote = listOf(remoteCopyOfA))
        assertEquals(listOf(a), result)
    }

    @Test
    fun `an empty remote is a no-op`() {
        assertEquals(listOf(a, b), mergeSavedArticles(local = listOf(a, b), remote = emptyList()))
    }

    @Test
    fun `an empty local adopts every remote save`() {
        assertEquals(setOf(a, b), mergeSavedArticles(local = emptyList(), remote = listOf(a, b)).toSet())
    }

    @Test
    fun `local order is preserved and remote-only items are appended`() {
        val result = mergeSavedArticles(local = listOf(b, a), remote = listOf(a, c))
        assertEquals(listOf(b, a, c), result)
    }

    @Test
    fun `nothing is lost when both sides are identical`() {
        val result = mergeSavedArticles(local = listOf(a, b), remote = listOf(a, b))
        assertEquals(listOf(a, b), result)
    }
}
