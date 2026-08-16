package com.mk.newsshorts.domain.search

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The folding is the whole feature for Arabic readers. Exact substring matching
 * would find a story only when the reader happened to spell it the same way the
 * publisher did, which for أ/ا, ة/ه and ى/ي is close to a coin toss — so each
 * of those habits gets a test naming the pair it protects.
 */
class SearchMatchingTest {

    // ---- folding ----

    @Test
    fun `the four alefs fold to one`() {
        val bare = normalizeForSearch("احمد")
        assertEquals(bare, normalizeForSearch("أحمد"))
        assertEquals(bare, normalizeForSearch("إحمد"))
        assertEquals(bare, normalizeForSearch("آحمد"))
    }

    @Test
    fun `ta marbuta and ha are the same ending`() {
        assertEquals(normalizeForSearch("غزه"), normalizeForSearch("غزة"))
    }

    @Test
    fun `alef maqsura and ya are the same ending`() {
        assertEquals(normalizeForSearch("مصطفي"), normalizeForSearch("مصطفى"))
    }

    @Test
    fun `diacritics are decoration and do not change a word`() {
        // مُحَمَّد, with damma, fatha and shadda set, is محمد.
        assertEquals(normalizeForSearch("محمد"), normalizeForSearch("مُحَمَّد"))
    }

    @Test
    fun `a dropped diacritic does not split the word it sat on`() {
        assertEquals(1, searchTokens("مُحَمَّد").size)
    }

    @Test
    fun `tatweel only stretches a word visually`() {
        assertEquals(normalizeForSearch("مصر"), normalizeForSearch("مـــصر"))
    }

    @Test
    fun `the seat a hamza sits on is house style rather than a different word`() {
        assertEquals(normalizeForSearch("مسئول"), normalizeForSearch("مسؤول"))
        assertEquals(normalizeForSearch("شئون"), normalizeForSearch("شؤون"))
    }

    @Test
    fun `a hamza is dropped but an alef under one is kept`() {
        assertEquals(normalizeForSearch("سما"), normalizeForSearch("سماء"))
        // Dropping the alef too would turn أحمد into a different name.
        assertEquals(normalizeForSearch("احمد"), normalizeForSearch("أحمد"))
    }

    @Test
    fun `arabic-indic digits name the same numbers as western ones`() {
        assertEquals("2026", normalizeForSearch("٢٠٢٦"))
    }

    @Test
    fun `latin marks and case fold too`() {
        assertEquals(normalizeForSearch("cafe"), normalizeForSearch("Café"))
        assertEquals(normalizeForSearch("uber"), normalizeForSearch("Über"))
        assertEquals("strasse", normalizeForSearch("Straße"))
    }

    @Test
    fun `punctuation separates words rather than joining them`() {
        assertEquals(listOf("covid", "19"), searchTokens("COVID-19"))
        assertEquals(listOf("covid", "19"), searchTokens("  covid   19  "))
    }

    @Test
    fun `a query of only punctuation asks for nothing rather than everything`() {
        assertEquals(emptyList(), searchTokens("!!! ??? —"))
        assertFalse(isSearchable("!!!"))
    }

    @Test
    fun `one character is too short to search for`() {
        assertFalse(isSearchable("a"))
        assertFalse(isSearchable("م"))
        assertTrue(isSearchable("مصر"))
    }

    @Test
    fun `the definite article is dropped from a query but not from a short word`() {
        assertEquals(listOf("حرب"), searchTokens("الحرب"))
        // الف is a word, not an article and two letters.
        assertEquals(listOf("الف"), searchTokens("الف"))
    }

    // ---- matching and ranking ----

    private var nextId = 0

    private fun article(
        title: String,
        summary: String = "",
        source: String = "Source",
        publishedAt: Long = 0L,
    ) = NewsArticle(
        id = ArticleId("a${nextId++}"),
        title = ArticleTitle(title),
        description = ArticleDescription(summary),
        content = ArticleContent(summary),
        author = ArticleAuthor(source),
        source = NewsSource(SourceId("s"), SourceName(source)),
        imageUrl = null,
        articleUrl = ArticleUrl("https://example.com/${nextId}"),
        publishedAt = PublishedTimestamp(publishedAt),
        category = NewsCategory.GENERAL,
    )

    @Test
    fun `a query typed one way finds a headline written the other`() {
        val published = article("قصف جديد على غزة")
        val index = SearchIndex.from(listOf(published))
        // Typed with ه instead of ة, and with a hamza the publisher omitted.
        assertEquals(listOf(published), index.search("غزه"))
    }

    @Test
    fun `a clitic glued to the front does not hide a word`() {
        val published = article("تطورات وغزة اليوم")
        assertEquals(listOf(published), SearchIndex.from(listOf(published)).search("غزة"))
    }

    @Test
    fun `every token has to match so a second word narrows the results`() {
        val both = article("انتخابات مصر المقبلة")
        val one = article("انتخابات فرنسا")
        val index = SearchIndex.from(listOf(both, one))
        assertEquals(listOf(both, one), index.search("انتخابات"))
        assertEquals(listOf(both), index.search("انتخابات مصر"))
    }

    @Test
    fun `a headline match outranks a summary match`() {
        val inSummary = article(title = "Something else entirely", summary = "A note about Cairo")
        val inTitle = article(title = "Cairo reopens the museum")
        val index = SearchIndex.from(listOf(inSummary, inTitle))
        assertEquals(listOf(inTitle, inSummary), index.search("cairo"))
    }

    @Test
    fun `two equally good matches are ordered newest first`() {
        val older = article(title = "Cairo today", publishedAt = 1_000L)
        val newer = article(title = "Cairo today", publishedAt = 2_000L)
        val index = SearchIndex.from(listOf(older, newer))
        assertEquals(listOf(newer, older), index.search("cairo"))
    }

    @Test
    fun `a publisher is searchable because readers look for outlets too`() {
        val published = article(title = "Markets slip", source = "BBC News")
        assertEquals(listOf(published), SearchIndex.from(listOf(published)).search("bbc"))
    }

    @Test
    fun `an empty query matches nothing rather than everything`() {
        val index = SearchIndex.from(listOf(article("Cairo"), article("Paris")))
        assertEquals(emptyList(), index.search(""))
        assertEquals(emptyList(), index.search("   "))
    }

    @Test
    fun `results are capped so a broad query stays a list`() {
        val many = (1..SearchIndex.MAX_RESULTS + 20).map { article("Cairo report $it") }
        assertEquals(SearchIndex.MAX_RESULTS, SearchIndex.from(many).search("cairo").size)
    }
}
