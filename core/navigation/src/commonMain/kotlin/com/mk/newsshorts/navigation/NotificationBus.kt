package com.mk.newsshorts.navigation

import com.mk.newsshorts.core.model.inbox.InboxNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notifications as they arrive, for whoever is listening.
 *
 * The inbox is published by the backend, but publishing is a static deploy that
 * lands minutes after the push does — so a reader who taps into the app right
 * after being notified would not find that notification in it. This carries the
 * one that just arrived straight across the process, and the published list
 * catches up on its own.
 *
 * A [StateFlow] for the same reason [DeepLinkBus] is one: the messaging service
 * can deliver while the ViewModel is still being built, and a replayless stream
 * would drop exactly the notification the reader is about to look for.
 *
 * Not consumed after reading. This is a record of what arrived, not a pending
 * action — re-collecting it merges the same notification into a list that
 * already has it, which is a no-op.
 */
class NotificationBus {

    private val mutableLatest = MutableStateFlow<InboxNotification?>(null)
    val latest: StateFlow<InboxNotification?> = mutableLatest.asStateFlow()

    fun post(notification: InboxNotification) {
        mutableLatest.value = notification
    }
}
