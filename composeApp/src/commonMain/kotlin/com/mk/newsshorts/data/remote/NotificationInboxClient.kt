package com.mk.newsshorts.data.remote

import io.ktor.client.call.body
import kotlinx.serialization.Serializable

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

    suspend fun fetch(language: String): List<SentNotificationDto> =
        runCatching {
            originClient.get(apiConfig.notificationsPath(language))
                .body<NotificationsResponseDto>()
                .notifications
        }.getOrElse { emptyList() }
}

@Serializable
data class NotificationsResponseDto(
    val notifications: List<SentNotificationDto> = emptyList(),
)

/**
 * [deepLink] is the same `newsshorts://article` link the notification itself
 * carried, so a tap here and a tap on the notification reach the article
 * through one parser.
 */
@Serializable
data class SentNotificationDto(
    val sentAt: Long = 0,
    val tier: String = "",
    val title: String = "",
    val body: String = "",
    val deepLink: String = "",
)
