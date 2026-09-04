package com.mk.newsshorts.core.data.remote

import com.mk.newsshorts.core.contract.notifications.NotificationsResponse
import com.mk.newsshorts.core.contract.notifications.SentNotification
import com.mk.newsshorts.core.domain.notifications.NotificationInboxFeed
import io.ktor.client.call.body

/**
 * What the backend has pushed, so the app can show a reader what they missed.
 *
 * Read from the server rather than collected as notifications arrive. A locally
 * built list would be empty on a new install, would miss anything delivered
 * while the app was force-stopped, and — because `NewsMessagingService` drops a
 * tier the reader has switched off before it is ever shown — would be missing
 * exactly the notifications an inbox exists to list.
 */
class NotificationInboxClient(
    private val originClient: OriginFailoverClient,
    private val apiConfig: ApiConfig,
) : NotificationInboxFeed {

    /**
     * Null when the fetch failed, as distinct from an empty list, which means
     * the reader genuinely has no notifications.
     *
     * This used to return an empty list for both, on the grounds that an inbox
     * which cannot be refreshed is worth nothing to a reader and worth even
     * less as a screen full of apology. That is still true of the *screen* —
     * but the caller could not tell the two apart, so a failed fetch on a cold
     * start left the unread badge empty with nothing to retry it. The screen
     * can go on saying nothing; the caller needs to know.
     */
    override suspend fun fetch(language: String): List<SentNotification>? =
        runCatching {
            originClient.get(apiConfig.notificationsPath(language))
                .body<NotificationsResponse>()
                .notifications
        }.getOrNull()
}
