package com.mk.newsshorts.domain.ranking

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
 * This ordering is what makes a returning reader see something new. Getting it
 * wrong in either direction is visible immediately — either yesterday's top
 * story is back on top, or a reader who wanted to revisit something can't
 * scroll to it at all.
 */
class SeenRankingTest {

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

    private val a = article("1", "https://example.com/a")
    private val b = article("2", "https://example.com/b")
    private val c = article("3", "https://example.com/c")

    @Test
    fun `an empty seen set changes nothing`() {
        assertEquals(listOf(a, b, c), listOf(a, b, c).deprioritiseSeen(emptySet()))
    }

    @Test
    fun `unseen articles come before seen ones`() {
        val seen = setOf(a.articleUrl.value.hashCode())
        assertEquals(listOf(b, c, a), listOf(a, b, c).deprioritiseSeen(seen))
    }

    @Test
    fun `order is preserved within each half`() {
        val seen = setOf(a.articleUrl.value.hashCode(), c.articleUrl.value.hashCode())
        // a and c are both seen; their relative order (a before c) must hold,
        // same for the untouched unseen half.
        assertEquals(listOf(b, a, c), listOf(a, b, c).deprioritiseSeen(seen))
    }

    @Test
    fun `every article seen is still every article just reordered to itself`() {
        val seen = listOf(a, b, c).map { it.articleUrl.value.hashCode() }.toSet()
        assertEquals(listOf(a, b, c), listOf(a, b, c).deprioritiseSeen(seen))
    }

    @Test
    fun `nothing is dropped only moved`() {
        val seen = setOf(b.articleUrl.value.hashCode())
        val result = listOf(a, b, c).deprioritiseSeen(seen)
        assertEquals(3, result.size)
        assertEquals(setOf(a, b, c), result.toSet())
    }
}
