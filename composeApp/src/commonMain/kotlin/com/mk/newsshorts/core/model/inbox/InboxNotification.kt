package com.mk.newsshorts.core.model.inbox

/**
 * One notification the backend has sent, as the inbox lists it.
 *
 * [deepLink] is the same `newsshorts://article` link the notification carried,
 * so opening a row and tapping the notification itself go through one parser.
 */
data class InboxNotification(
    val sentAt: Long,
    val title: String,
    val body: String,
    val deepLink: String,
    /**
     * Pulled out of [deepLink] once, when the list is built, because it is the
     * key the read marks hang on - and re-parsing the link for every row on
     * every recomposition to find it would be work for nothing.
     */
    val articleUrl: String,
)
