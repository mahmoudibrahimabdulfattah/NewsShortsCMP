package com.mk.newsshorts.presentation.mvi

import com.mk.newsshorts.data.local.InboxReadState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The count behind the bell, and the marks on the rows — one derivation feeds
 * both, so they can never disagree.
 */
class UnreadInboxCountTest {

    private fun state(read: InboxReadState, vararg sentAt: Long) = NewsUiState(
        inboxNotifications = sentAt.map {
            InboxNotification(sentAt = it, title = "t", body = "b", deepLink = "newsshorts://article?url=x")
        },
        inboxRead = read,
    )

    /**
     * A reader who has done nothing has read nothing — including after a
     * reinstall, where the published list survives and the local state does not.
     */
    @Test
    fun `everything is unread until something clears it`() {
        assertEquals(3, state(InboxReadState(), 3_000L, 2_000L, 1_000L).unreadInboxCount)
    }

    /**
     * The bug this replaced: opening the inbox used to stamp everything read,
     * so a reader tapped a bell showing three and found nothing marked. Looking
     * at a list is not reading what is in it.
     */
    @Test
    fun `looking at the inbox clears nothing`() {
        val onOpen = state(InboxReadState(), 3_000L, 2_000L, 1_000L)

        // Nothing about opening the screen touches this state at all.
        assertEquals(3, onOpen.unreadInboxCount)
        assertEquals(setOf(3_000L, 2_000L, 1_000L), onOpen.unreadInboxIds)
    }

    /** The first of the two things that does clear one. */
    @Test
    fun `opening one notification clears only that one`() {
        val after = state(InboxReadState(readIds = setOf(2_000L)), 3_000L, 2_000L, 1_000L)

        assertEquals(2, after.unreadInboxCount)
        assertEquals(setOf(3_000L, 1_000L), after.unreadInboxIds)
    }

    /** The second. */
    @Test
    fun `marking all read clears every mark in the list`() {
        val after = state(InboxReadState(readAllBefore = 3_000L), 3_000L, 2_000L, 1_000L)

        assertEquals(0, after.unreadInboxCount)
        assertEquals(emptySet(), after.unreadInboxIds)
    }

    /**
     * And a notification that arrives after the sweep is unread again, which is
     * what stops "mark all read" from being a permanent mute.
     */
    @Test
    fun `a notification arriving after the sweep is unread`() {
        val after = state(InboxReadState(readAllBefore = 3_000L), 4_000L, 3_000L, 2_000L)

        assertEquals(1, after.unreadInboxCount)
        assertEquals(setOf(4_000L), after.unreadInboxIds)
    }

    @Test
    fun `an empty inbox counts nothing`() {
        assertEquals(0, state(InboxReadState()).unreadInboxCount)
    }
}
