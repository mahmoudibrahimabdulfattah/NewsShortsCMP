package com.mk.newsshorts.core.domain.notifications

import com.mk.newsshorts.core.contract.notifications.SentNotification

/**
 * What the backend has pushed, so the app can show a reader what they missed.
 */
interface NotificationInboxFeed {
    /**
     * Null when the fetch failed, as distinct from an empty list, which means
     * the reader genuinely has no notifications.
     *
     * The distinction matters to the unread badge: an implementation that
     * reported both as "empty" left a cold start with no network showing no
     * mark, with nothing to retry it.
     */
    suspend fun fetch(language: String): List<SentNotification>?
}
