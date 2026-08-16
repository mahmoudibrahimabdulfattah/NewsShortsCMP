package com.mk.newsshorts.server.store

import com.mk.newsshorts.server.feed.FeedLayout
import com.mk.newsshorts.server.feed.FeedPage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The page layout only holds a boundary still if it survives the process that
 * wrote it: every publish is a fresh JVM against a database CI restored from
 * the last run.
 */
class FeedLayoutStoreTest {

    private fun store(): ArticleStore {
        val db = File.createTempFile("feed-layout", ".db").apply { deleteOnExit() }
        return ArticleStore(db.absolutePath)
    }

    @Test
    fun `a feed with no history starts empty`() {
        assertEquals(FeedLayout.EMPTY, store().feedLayout("en-general"))
    }

    @Test
    fun `pages come back in reading order with their contents in place`() {
        val store = store()
        val layout = FeedLayout(
            pages = listOf(
                FeedPage(3, listOf(30L, 29L, 28L)),
                FeedPage(2, listOf(27L, 26L)),
                FeedPage(1, listOf(25L, 24L)),
            ),
            placedIds = (24L..30L).toSet(),
        )

        store.saveFeedLayout("en-general", layout)

        assertEquals(layout, store.feedLayout("en-general"))
    }

    @Test
    fun `an empty head page keeps its number across a publish`() {
        val store = store()
        val layout = FeedLayout(
            pages = listOf(FeedPage(4, emptyList()), FeedPage(3, listOf(10L, 9L))),
            placedIds = setOf(9L, 10L, 11L, 12L),
        )

        store.saveFeedLayout("ar-sports", layout)
        val restored = store.feedLayout("ar-sports")

        assertEquals(4, restored.head!!.number)
        assertTrue(restored.head!!.articleIds.isEmpty())
        assertEquals(setOf(9L, 10L, 11L, 12L), restored.placedIds)
    }

    @Test
    fun `one feed's layout does not touch another's`() {
        val store = store()
        store.saveFeedLayout("en-general", layoutOf(2, 5L))
        store.saveFeedLayout("ar-general", layoutOf(7, 9L))

        store.saveFeedLayout("en-general", layoutOf(3, 6L))

        assertEquals(layoutOf(7, 9L), store.feedLayout("ar-general"))
    }

    @Test
    fun `a rewritten layout replaces the old one rather than adding to it`() {
        val store = store()
        store.saveFeedLayout("en-general", FeedLayout(listOf(FeedPage(2, listOf(5L, 4L))), setOf(4L, 5L)))

        store.saveFeedLayout("en-general", FeedLayout(listOf(FeedPage(2, listOf(5L))), setOf(4L, 5L)))

        assertEquals(listOf(5L), store.feedLayout("en-general").head!!.articleIds)
    }

    @Test
    fun `an article that leaves the pages is still remembered as published`() {
        // The whole point of keeping this separately from the pages: retention
        // takes an article off its page, and it must not then be served again
        // as though the feed had never carried it.
        val store = store()
        store.saveFeedLayout("en-general", FeedLayout(listOf(FeedPage(2, listOf(5L, 4L))), setOf(4L, 5L)))

        store.saveFeedLayout("en-general", FeedLayout(listOf(FeedPage(2, listOf(5L))), setOf(5L)))

        assertTrue(4L in store.feedLayout("en-general").placedIds)
    }

    private fun layoutOf(page: Int, id: Long) =
        FeedLayout(listOf(FeedPage(page, listOf(id))), setOf(id))
}
