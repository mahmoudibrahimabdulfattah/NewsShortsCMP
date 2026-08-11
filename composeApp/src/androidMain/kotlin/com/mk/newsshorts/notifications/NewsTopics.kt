package com.mk.newsshorts.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Push delivery is by topic rather than per-device token, so no user database
 * is needed to reach the right readers: one topic per news language, and the
 * publish workflow sends to whichever it just found a story for.
 */
object NewsTopics {

    private const val PREFS = "news_topics"
    private const val KEY_LANGUAGE = "subscribed_language"

    private fun topicFor(language: String): String = "news_$language"

    /**
     * Moves the device onto the topic for [language], leaving the previous one.
     * Safe to call on every launch — it only touches Firebase when the
     * language actually changed.
     */
    fun subscribe(context: Context, language: String) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString(KEY_LANGUAGE, null)
        if (previous == language) return

        val messaging = FirebaseMessaging.getInstance()
        previous?.let { messaging.unsubscribeFromTopic(topicFor(it)) }
        messaging.subscribeToTopic(topicFor(language))
            .addOnSuccessListener { prefs.edit().putString(KEY_LANGUAGE, language).apply() }
    }

    /** Re-applies the stored subscription, e.g. after the FCM token rotates. */
    fun resubscribe(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val language = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null) ?: return
        FirebaseMessaging.getInstance().subscribeToTopic(topicFor(language))
    }
}
