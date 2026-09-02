package com.mk.newsshorts.core.data.sync

import com.mk.newsshorts.core.model.sync.SyncedSettings
import com.mk.newsshorts.core.model.sync.toAppPreferences
import com.mk.newsshorts.core.data.local.SettingsManager

suspend fun SettingsManager.apply(settings: SyncedSettings) {
    apply(settings.toAppPreferences(preferences.value))
}
