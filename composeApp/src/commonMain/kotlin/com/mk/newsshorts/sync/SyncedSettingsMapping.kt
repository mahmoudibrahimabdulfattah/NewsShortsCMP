package com.mk.newsshorts.sync

import com.mk.newsshorts.data.local.AppPreferences

/**
 * The stored preferences as the server sees them. `textScale` is deliberately
 * absent: it is a per-device reading comfort, not an account setting, and the
 * synced shape has never carried it.
 */
fun AppPreferences.toSyncedSettings(): SyncedSettings = SyncedSettings(
    newsLanguage = newsLanguage,
    appLocale = appLocale,
    selectedCountry = selectedCountry,
    themeMode = themeMode,
    notificationsEnabled = notificationsEnabled,
    notifyBreaking = notifyBreaking,
    notifyTopStory = notifyTopStory,
    notifyReminder = notifyReminder,
)
