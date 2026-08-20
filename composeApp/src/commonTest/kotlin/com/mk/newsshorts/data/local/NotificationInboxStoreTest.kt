package com.mk.newsshorts.data.local

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Two things clear an unread mark and nothing else does: opening that story —
 * from the inbox or from the notification in the tray — or marking them all.
 * What this decodes to is what a reader sees marked, so a wrong answer here
 * quietly loses a story they were told about.
 */
class NotificationInboxStoreTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val story = "https://example.com/story"

    @Test
    fun `an unset value reads as nothing read`() {
        assertEquals(InboxReadState(), decodeInboxReadState("", json))
    }

    @Test
    fun `a stored state round-trips`() {
        val state = InboxReadState(readAllBefore = 1_000L, readArticles = setOf(7, 9))

        assertEquals(state, decodeInboxReadState(json.encodeToString(state), json))
    }

    /**
     * Nothing read, not everything read: showing a mark on a story already
     * opened is a moment's confusion the reader can clear, while hiding one
     * they have not seen loses it for good.
     */
    @Test
    fun `an unreadable value reads as nothing read`() {
        assertEquals(InboxReadState(), decodeInboxReadState("{not valid json", json))
        assertEquals(InboxReadState(), decodeInboxReadState("[]", json))
    }

    @Test
    fun `the sweep covers everything sent at or below it`() {
        val state = InboxReadState(readAllBefore = 2_000L)

        assertTrue(state.isRead(1_000L, story))
        assertTrue(state.isRead(2_000L, story))
        assertFalse(state.isRead(2_001L, story))
    }

    /**
     * The tray case. A push tapped from outside the app arrives carrying the
     * article's URL and nothing about the send, so the mark has to hang on the
     * article — and it has to hold whatever `sentAt` the published list turns
     * out to carry for it.
     */
    @Test
    fun `an opened article is read whenever it was sent`() {
        val state = InboxReadState(readArticles = setOf(articleKey(story)))

        assertTrue(state.isRead(9_000L, story))
        assertTrue(state.isRead(1L, story))
        assertFalse(state.isRead(9_000L, "https://example.com/other"))
    }

    /** A URL out of a JSON field can arrive padded; the same story is one key. */
    @Test
    fun `the key ignores surrounding whitespace`() {
        assertEquals(articleKey(story), articleKey("  $story\n"))
    }

    @Test
    fun `nothing is dismissed until something is`() {
        assertEquals(emptySet(), decodeDismissed("", json))
    }

    /**
     * Unreadable shows the row rather than hiding it. A row a reader has
     * already dealt with reappearing is a small annoyance; one that vanishes
     * because a value could not be parsed is a story they never learn they
     * were sent.
     */
    @Test
    fun `an unreadable dismissal list hides nothing`() {
        assertEquals(emptySet(), decodeDismissed("{not valid json", json))
        assertEquals(emptySet(), decodeDismissed("[]", json))
    }
}
