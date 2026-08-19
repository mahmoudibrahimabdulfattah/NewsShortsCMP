package com.mk.newsshorts.data.local

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Two things clear an unread mark and nothing else does: opening that
 * notification, or marking them all. What this decodes to is what a reader sees
 * marked, so a wrong answer here quietly loses a story they were told about.
 */
class NotificationInboxStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `an unset value reads as nothing read`() {
        assertEquals(InboxReadState(), decodeInboxReadState("", json))
    }

    @Test
    fun `a stored state round-trips`() {
        val state = InboxReadState(readAllBefore = 1_000L, readIds = setOf(2_000L, 3_000L))

        val decoded = decodeInboxReadState(json.encodeToString(state), json)

        assertEquals(state, decoded)
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
    fun `the sweep covers everything at or below it`() {
        val state = InboxReadState(readAllBefore = 2_000L)

        assertTrue(state.isRead(1_000L))
        assertTrue(state.isRead(2_000L))
        assertFalse(state.isRead(2_001L))
    }

    /** One notification opened, above the sweep, is read on its own. */
    @Test
    fun `an individually opened notification is read`() {
        val state = InboxReadState(readAllBefore = 1_000L, readIds = setOf(3_000L))

        assertTrue(state.isRead(3_000L))
        assertFalse(state.isRead(2_000L))
    }
}
