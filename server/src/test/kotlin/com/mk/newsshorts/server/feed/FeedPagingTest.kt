package com.mk.newsshorts.server.feed

import com.mk.newsshorts.core.contract.feed.FeedArticleDto
import com.mk.newsshorts.server.store.interleaveBySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The case this exists for: articles arrive at the front of the feed every half
 * hour. A page defined as "ranks 41-80 of whatever is published right now" is a
 * different set of articles after every cycle, so a reader who loaded the first
 * page half an hour ago would meet some of it again on the second — or scroll
 * straight past a story that slid down into a page they had already left.
 */
class FeedPagingTest {

    private val pageSize = 10

    /**
     * Stands in for the article table across publishes: ids ascend with
     * ingestion, the feed reads newest first, and retention takes the oldest.
     */
    private class Store {
        private var nextId = 0L
        private val live = ArrayList<Long>()

        fun arrive(count: Int): Store = apply { repeat(count) { live += ++nextId } }
        fun expire(count: Int): Store = apply { repeat(count) { live.removeAt(0) } }
        fun order(): List<Long> = live.sortedDescending()
    }

    /**
     * What a reader who downloaded [entry] actually gets, following the chain
     * frozen into the file they hold rather than one published afterwards.
     */
    private fun readDown(entry: FeedLayout, servedBy: FeedLayout): List<Long> {
        val read = ArrayList<Long>()
        read += entry.pages.first().articleIds
        entry.pages.drop(1).forEach { page ->
            // A page that has since been emptied by retention is the end of the
            // feed, which is what the client sees as a 404.
            val served = servedBy.pages.firstOrNull { it.number == page.number } ?: return read
            read += served.articleIds
        }
        return read
    }

    @Test
    fun `a first publish splits the feed newest first`() {
        val store = Store().arrive(25)

        val layout = repaginate(FeedLayout.EMPTY, store.order(), pageSize)

        assertEquals(listOf(5, 10, 10), layout.pages.map { it.articleIds.size })
        assertEquals(store.order(), layout.pages.flatMap { it.articleIds })
    }

    @Test
    fun `page numbers descend in reading order`() {
        val layout = repaginate(FeedLayout.EMPTY, Store().arrive(25).order(), pageSize)

        assertEquals(layout.pages.map { it.number }.sortedDescending(), layout.pages.map { it.number })
    }

    @Test
    fun `a sealed page is never rewritten`() {
        val store = Store().arrive(25)
        val first = repaginate(FeedLayout.EMPTY, store.order(), pageSize)
        val sealedBefore = first.pages.drop(1)

        val second = repaginate(first, store.arrive(30).order(), pageSize)

        sealedBefore.forEach { before ->
            val after = second.pages.first { it.number == before.number }
            assertEquals(before.articleIds, after.articleIds, "page ${before.number} moved")
        }
    }

    @Test
    fun `a reader part way down the feed sees no article twice`() {
        val store = Store().arrive(25)
        val first = repaginate(FeedLayout.EMPTY, store.order(), pageSize)
        val second = repaginate(first, store.arrive(30).order(), pageSize)
        val third = repaginate(second, store.arrive(12).order(), pageSize)

        val read = readDown(entry = first, servedBy = third)

        assertEquals(read.size, read.toSet().size, "read the same article twice: $read")
    }

    @Test
    fun `a reader part way down the feed skips nothing below where they started`() {
        val store = Store().arrive(25)
        val started = store.order()
        val first = repaginate(FeedLayout.EMPTY, started, pageSize)
        val second = repaginate(first, store.arrive(30).order(), pageSize)
        val third = repaginate(second, store.arrive(12).order(), pageSize)

        val read = readDown(entry = first, servedBy = third)

        // Everything that existed when they started is still reachable, in the
        // order it had then. What arrived afterwards is not on their path, and
        // should not be: it belongs above where they are reading, which is what
        // pulling to refresh is for.
        assertEquals(started, read)
    }

    @Test
    fun `a page sealed after the reader started is not on their path`() {
        val store = Store().arrive(25)
        val first = repaginate(FeedLayout.EMPTY, store.order(), pageSize)
        val second = repaginate(first, store.arrive(30).order(), pageSize)

        val sealedSince = second.pages.map { it.number } - first.pages.map { it.number }.toSet()

        assertTrue(sealedSince.isNotEmpty(), "the second publish sealed nothing, so this proves nothing")
        assertTrue(first.pages.none { it.number in sealedSince })
    }

    @Test
    fun `new articles never land in a page that was already sealed`() {
        val store = Store().arrive(25)
        val first = repaginate(FeedLayout.EMPTY, store.order(), pageSize)
        val alreadySealed = first.pages.drop(1).map { it.number }.toSet()
        val arrived = (26L..31L).toSet()

        val second = repaginate(first, store.arrive(6).order(), pageSize)

        second.pages.filter { it.number in alreadySealed }.forEach { page ->
            assertTrue(
                page.articleIds.none { it in arrived },
                "page ${page.number} took a new article after it was sealed",
            )
        }
        assertTrue(second.pages.flatMap { it.articleIds }.containsAll(arrived))
    }

    @Test
    fun `a pruned article leaves its page`() {
        val store = Store().arrive(25)
        val first = repaginate(FeedLayout.EMPTY, store.order(), pageSize)

        val second = repaginate(first, store.expire(5).order(), pageSize)

        assertEquals(store.order(), second.pages.flatMap { it.articleIds })
    }

    @Test
    fun `an emptied page disappears rather than being renumbered`() {
        val store = Store().arrive(25)
        val first = repaginate(FeedLayout.EMPTY, store.order(), pageSize)
        val deepest = first.pages.last().number

        val second = repaginate(first, store.expire(10).order(), pageSize)

        assertTrue(second.pages.none { it.number == deepest })
        assertEquals(
            first.pages.drop(1).dropLast(1).map { it.number },
            second.pages.drop(1).map { it.number },
        )
    }

    @Test
    fun `a retired article cannot come back at the top of the feed`() {
        val store = Store().arrive(25)
        val first = repaginate(FeedLayout.EMPTY, store.order(), pageSize)
        val pruned = repaginate(first, store.expire(5).order(), pageSize)

        // The same ids turn up again — a source republishing what it already
        // published, a re-run against a restored database. They were placed
        // once, and must not be served as though they were new.
        val third = repaginate(pruned, (1L..25L).toList().sortedDescending(), pageSize)

        assertTrue(third.head!!.articleIds.none { it <= 5 }, "a retired article returned to the head")
    }

    @Test
    fun `an empty head page keeps its number`() {
        // Reached when retention takes everything the head was holding: the
        // head has nothing to publish, but handing its number to a later page
        // would give two different sets of articles the same file name.
        val previous = FeedLayout(
            pages = listOf(FeedPage(3, listOf(30L, 29L)), FeedPage(2, (28L downTo 19L).toList())),
            placedIds = (19L..30L).toSet(),
        )

        val layout = repaginate(previous, (28L downTo 19L).toList(), pageSize)

        assertEquals(3, layout.head!!.number)
        assertTrue(layout.head!!.articleIds.isEmpty())
    }

    @Test
    fun `an empty feed still has a head page to publish`() {
        val layout = repaginate(FeedLayout.EMPTY, order = emptyList(), pageSize = pageSize)

        assertEquals(1, layout.pages.size)
        assertTrue(layout.pages.single().articleIds.isEmpty())
    }

    @Test
    fun `the publisher mix holds across a page boundary`() {
        // Interleaving runs over the whole depth before it is split. Paging
        // each window separately instead would hand the first slots of every
        // page to whoever publishes most often.
        val mixed = (List(20) { article("اليوم السابع") } + List(20) { article("BBC عربي") })
            .interleaveBySource()
        val layout = repaginate(FeedLayout.EMPTY, mixed.map { it.id }, pageSize)

        val bySource = mixed.associateBy { it.id }
        layout.pages.forEach { page ->
            val opening = page.articleIds.take(4).map { bySource.getValue(it).sourceName }
            assertEquals(2, opening.toSet().size, "page ${page.number} opens with $opening")
        }
    }

    @Test
    fun `the file chain runs from the head to the last page and stops`() {
        val layout = repaginate(FeedLayout.EMPTY, Store().arrive(25).order(), pageSize)

        val names = layout.pages.indices.map { FeedPageNames.fileFor("en-general", layout, it) }

        assertEquals("en-general.json", names.first())
        assertEquals(listOf("en-general.json", "en-general-p2.json", "en-general-p1.json"), names)
        assertNull(layout.pages.getOrNull(names.size))
    }

    @Test
    fun `an article translated a cycle late still reaches the feed`() {
        // Ids are handed out at ingestion; an article joins *this* feed only
        // once its text exists in this feed's language, and rendering is
        // budgeted and runs newest first. So an article can be ingested before
        // articles that are already published here and arrive afterwards, with
        // a lower id than all of them. It is new to this feed and belongs in it.
        val first = repaginate(FeedLayout.EMPTY, listOf(30L, 29L, 28L), pageSize)

        val second = repaginate(first, listOf(30L, 29L, 28L, 27L), pageSize)

        assertTrue(
            second.pages.flatMap { it.articleIds }.contains(27L),
            "a late translation never reached the feed: ${second.pages.map { it.articleIds }}",
        )
    }

    @Test
    fun `a late translation still cannot displace a sealed page`() {
        // id 3 was ingested near the beginning but has no text in this feed's
        // language yet, so the first publish goes out without it.
        val all = (25L downTo 1L).toList()
        val first = repaginate(FeedLayout.EMPTY, all - 3L, pageSize)
        val sealedBefore = first.pages.drop(1)

        val second = repaginate(first, all, pageSize)

        sealedBefore.forEach { before ->
            val after = second.pages.first { it.number == before.number }
            assertEquals(before.articleIds, after.articleIds, "page ${before.number} moved")
        }
        assertTrue(second.head!!.articleIds.contains(3L))
    }

    @Test
    fun `a layout rebuilt from nothing does not reuse page names`() {
        // What losing the CI cache looks like: the feed has a published history
        // that nothing in this process knows about. Numbering from 1 again would
        // hand `-p1.json` to a different set of articles under a name readers
        // already hold.
        val published = repaginate(FeedLayout.EMPTY, Store().arrive(25).order(), pageSize)

        val rebuilt = repaginate(FeedLayout.EMPTY, Store().arrive(25).order(), pageSize, firstNumber = 91)

        val reused = published.pages.map { it.number }.intersect(rebuilt.pages.map { it.number }.toSet())
        assertTrue(reused.isEmpty(), "page numbers $reused would be republished with other articles")
    }

    private var nextArticleId = 100L

    private fun article(source: String) = FeedArticleDto(
        id = nextArticleId++,
        title = source,
        summary = "summary",
        url = "https://example.com/$source/$nextArticleId",
        imageUrl = null,
        sourceName = source,
        language = "ar",
        category = "general",
        publishedAt = nextArticleId,
    )
}
