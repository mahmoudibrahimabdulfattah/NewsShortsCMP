package com.mk.newsshorts.server.store

import com.mk.newsshorts.core.contract.feed.FeedArticleDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The case this exists for: two Egyptian dailies publishing every few minutes
 * held five of the first ten slots in the general Arabic feed, so "For You"
 * served the same stories in the same order as the Egypt tab.
 */
class SourceMixTest {

    private var nextId = 0L

    private fun article(source: String, publishedAt: Long = nextId) = FeedArticleDto(
        id = nextId++,
        title = "$source $publishedAt",
        summary = "summary",
        url = "https://example.com/$source/$publishedAt",
        imageUrl = null,
        sourceName = source,
        language = "ar",
        category = "general",
        publishedAt = publishedAt,
    )

    @Test
    fun `alternates between publishers`() {
        val mixed = listOf(
            article("A"), article("A"), article("A"),
            article("B"), article("B"), article("B"),
        ).interleaveBySource()

        assertEquals(listOf("A", "B", "A", "B", "A", "B"), mixed.map { it.sourceName })
    }

    @Test
    fun `a flooding publisher cannot take the whole front of the feed`() {
        val flooded = List(20) { article("اليوم السابع") } +
            listOf(article("BBC عربي"), article("RT Arabic"), article("CNN بالعربية"))

        val front = flooded.interleaveBySource().take(6).map { it.sourceName }

        assertEquals(4, front.toSet().size, "the first six slots came from $front")
    }

    @Test
    fun `keeps every article rather than trimming the loudest source`() {
        val original = List(9) { article("A") } + List(2) { article("B") }
        val mixed = original.interleaveBySource()

        assertEquals(original.size, mixed.size)
        assertEquals(original.map { it.id }.toSet(), mixed.map { it.id }.toSet())
    }

    @Test
    fun `holds newest-first inside one publisher`() {
        val mixed = (
            listOf(article("A", publishedAt = 300), article("A", publishedAt = 200), article("A", publishedAt = 100)) +
                listOf(article("B", publishedAt = 250))
            ).interleaveBySource()

        val a = mixed.filter { it.sourceName == "A" }.map { it.publishedAt }
        assertEquals(listOf(300L, 200L, 100L), a)
    }

    @Test
    fun `leads with the freshest story overall`() {
        // Sources are visited in the order their newest article arrived, so
        // mixing never buries the actual top story.
        val mixed = (listOf(article("B", publishedAt = 900)) + List(5) { article("A", publishedAt = 100) })
            .interleaveBySource()

        assertEquals("B", mixed.first().sourceName)
    }

    @Test
    fun `a single publisher is left alone`() {
        val only = List(4) { article("A") }
        assertEquals(only.map { it.id }, only.interleaveBySource().map { it.id })
    }

    @Test
    fun `an empty feed stays empty`() {
        assertTrue(emptyList<FeedArticleDto>().interleaveBySource().isEmpty())
    }
}
