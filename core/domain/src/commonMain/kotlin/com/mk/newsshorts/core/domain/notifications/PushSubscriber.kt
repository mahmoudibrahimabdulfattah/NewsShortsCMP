package com.mk.newsshorts.core.domain.notifications

/**
 * Chooses which pushes this device should receive.
 *
 * Delivery is by topic rather than per-device token, so reaching the right
 * readers needs no accounts and no user database on the backend.
 */
interface PushSubscriber {
    /** Receive breaking news in [language], leaving any previous language. */
    fun subscribeToLanguage(language: String)

    /**
     * Leaves every topic. Called when the reader turns notifications off in
     * Settings, alongside the local gate in the messaging service — belt and
     * braces, so anything already queued server-side still gets filtered even
     * if the unsubscribe has not landed yet.
     */
    fun unsubscribeAll()
}
