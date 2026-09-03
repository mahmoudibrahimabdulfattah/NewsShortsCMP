package com.mk.newsshorts.core.contract.notifications

import kotlinx.serialization.Serializable

@Serializable
data class NotificationsResponse(
    // The client may read an older server response, so contract defaults stay
    // here even though the server only writes this shape.
    val notifications: List<SentNotification> = emptyList(),
)

/**
 * One notification as the in-app inbox shows it.
 *
 * [deepLink] is the same `newsshorts://article` link the push itself carried,
 * so a tap in the inbox and a tap on the notification reach the article
 * through one parser. Never null on the server: the reminder tier has no
 * article and is left out of the inbox entirely.
 *
 * The client may read an older server response, so field defaults stay here
 * even though the server only writes this shape.
 */
@Serializable
data class SentNotification(
    val sentAt: Long = 0,
    val tier: String = "",
    val title: String = "",
    val body: String = "",
    val deepLink: String = "",
)
