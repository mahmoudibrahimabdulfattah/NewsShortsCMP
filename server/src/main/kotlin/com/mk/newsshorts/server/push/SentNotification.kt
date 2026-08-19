package com.mk.newsshorts.server.push

import kotlinx.serialization.Serializable

/**
 * One notification as the in-app inbox shows it.
 *
 * [deepLink] is the same `newsshorts://article` link the push itself carried, so
 * a tap in the inbox and a tap on the notification reach the article through one
 * parser. Never null here: the reminder tier has no article and is left out of
 * the inbox entirely.
 */
@Serializable
data class SentNotification(
    val sentAt: Long,
    val tier: String,
    val title: String,
    val body: String,
    val deepLink: String,
)

@Serializable
data class NotificationsResponse(
    val notifications: List<SentNotification>,
)
