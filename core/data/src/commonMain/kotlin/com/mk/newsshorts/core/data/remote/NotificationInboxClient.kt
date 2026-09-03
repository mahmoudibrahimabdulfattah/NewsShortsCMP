package com.mk.newsshorts.core.data.remote

import com.mk.newsshorts.core.contract.notifications.NotificationsResponse
import com.mk.newsshorts.core.contract.notifications.SentNotification
import io.ktor.client.call.body

/**
 * What the backend has pushed, so the app can show a reader what they missed.
 *
 * Read from the server rather than collected as notifications arrive. A locally
 * built list would be empty on a new install, would miss anything delivered
 * while the app was force-stopped, and — because `NewsMessagingService` drops a
 * tier the reader has switched off before it is ever shown — would be missing
 * exactly the notifications an inbox exists to list.
 *
 * Failure returns an empty list, not an error: an inbox that cannot be refreshed
 * is worth nothing to a reader, and worth even less as a screen full of
 * apology.
 */
class NotificationInboxClient(
    private val originClient: OriginFailoverClient,
    private val apiConfig: ApiConfig,
) {

    suspend fun fetch(language: String): List<SentNotification> =
        runCatching {
            originClient.get(apiConfig.notificationsPath(language))
                .body<NotificationsResponse>()
                .notifications
        }.getOrElse { emptyList() }
}
