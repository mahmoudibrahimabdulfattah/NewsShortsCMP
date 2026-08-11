package com.mk.newsshorts.notifications

/**
 * Chooses which pushes this device should receive.
 *
 * Delivery is by topic rather than per-device token, so reaching the right
 * readers needs no accounts and no user database on the backend.
 */
interface PushSubscriber {
    /** Receive breaking news in [language], leaving any previous language. */
    fun subscribeToLanguage(language: String)
}

/** Used on targets without push support. */
object NoOpPushSubscriber : PushSubscriber {
    override fun subscribeToLanguage(language: String) = Unit
}
