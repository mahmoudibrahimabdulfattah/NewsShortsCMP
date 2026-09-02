package com.mk.newsshorts.core.model.sync

import com.mk.newsshorts.core.model.settings.AppPreferences

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

fun SyncedSettings.toAppPreferences(current: AppPreferences): AppPreferences = current.copy(
    newsLanguage = newsLanguage,
    appLocale = appLocale,
    selectedCountry = selectedCountry,
    themeMode = themeMode,
    notificationsEnabled = notificationsEnabled,
    notifyBreaking = notifyBreaking,
    notifyTopStory = notifyTopStory,
    notifyReminder = notifyReminder,
)
