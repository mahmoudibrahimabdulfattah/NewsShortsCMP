package com.mk.newsshorts.core.model.settings

/**
 * The `SettingsStorage` keys for the notification switches, shared between
 * [SettingsManager] (read by the app) and `NewsMessagingService` (read by a
 * system-instantiated Android service that cannot go through Koin, so it opens
 * the Android storage directly). One object means the two call sites cannot
 * drift apart on a key name.
 *
 * Values are the string `"true"`/`"false"` rather than a boolean type, matching
 * `SettingsStorage`'s string-only API and the existing pattern in
 * `SettingsManager.securityWarningSeen()`.
 */
object NotificationPreferenceKeys {
    const val ENABLED: String = "notifications_enabled"
    const val NOTIFY_BREAKING: String = "notify_breaking"
    const val NOTIFY_TOP_STORY: String = "notify_top_story"
    const val NOTIFY_REMINDER: String = "notify_reminder"

    /** All tiers default on: the reader who never opens Settings gets everything. */
    const val DEFAULT_ENABLED: String = "true"

    /** The "tier" value the server puts in the FCM data payload for each key. */
    fun keyForWireTier(tier: String): String? = when (tier) {
        "breaking" -> NOTIFY_BREAKING
        "top_story" -> NOTIFY_TOP_STORY
        "reminder" -> NOTIFY_REMINDER
        else -> null
    }

    /**
     * The gating rule itself, kept free of `SettingsStorage` so it is testable
     * on its own: the master switch always wins, and a tier this build does
     * not recognise (a future server addition) is allowed through rather than
     * silently dropped.
     */
    fun isAllowed(masterEnabled: Boolean, tierEnabled: Boolean?): Boolean {
        if (!masterEnabled) return false
        return tierEnabled ?: true
    }
}
