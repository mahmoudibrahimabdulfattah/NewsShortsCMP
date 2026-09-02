package com.mk.newsshorts.core.domain.settings

import com.mk.newsshorts.core.model.settings.AppPreferences
import kotlinx.coroutines.flow.StateFlow

interface SettingsPersistence {
    val preferences: StateFlow<AppPreferences>

    suspend fun saveAppLocale(localeCode: String)
    suspend fun saveThemeMode(mode: String)
    suspend fun saveTextScale(scale: String)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setNotifyBreaking(enabled: Boolean)
    suspend fun setNotifyTopStory(enabled: Boolean)
    suspend fun setNotifyReminder(enabled: Boolean)
}
