package com.mk.newsshorts.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class SettingsManager(
    private val settingsStorage: SettingsStorage
) {
    private val newsLanguageState: MutableStateFlow<String> = MutableStateFlow(DEFAULT_NEWS_LANGUAGE)
    private val appLocaleState: MutableStateFlow<String> = MutableStateFlow(DEFAULT_APP_LOCALE)
    private val selectedCountryState: MutableStateFlow<String> = MutableStateFlow(DEFAULT_COUNTRY)
    private val themeModeState: MutableStateFlow<String> = MutableStateFlow(DEFAULT_THEME_MODE)
    private val notificationsEnabledState: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val notifyBreakingState: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val notifyTopStoryState: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val notifyReminderState: MutableStateFlow<Boolean> = MutableStateFlow(true)

    val newsLanguageFlow: Flow<String> = newsLanguageState.asStateFlow()
    val appLocaleFlow: Flow<String> = appLocaleState.asStateFlow()
    val selectedCountryFlow: Flow<String> = selectedCountryState.asStateFlow()
    val themeModeFlow: Flow<String> = themeModeState.asStateFlow()
    val notificationsEnabledFlow: Flow<Boolean> = notificationsEnabledState.asStateFlow()
    val notifyBreakingFlow: Flow<Boolean> = notifyBreakingState.asStateFlow()
    val notifyTopStoryFlow: Flow<Boolean> = notifyTopStoryState.asStateFlow()
    val notifyReminderFlow: Flow<Boolean> = notifyReminderState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        newsLanguageState.value = settingsStorage.getString(KEY_NEWS_LANGUAGE, DEFAULT_NEWS_LANGUAGE)
        appLocaleState.value = settingsStorage.getString(KEY_APP_LOCALE, DEFAULT_APP_LOCALE)
        selectedCountryState.value = settingsStorage.getString(KEY_SELECTED_COUNTRY, DEFAULT_COUNTRY)
        themeModeState.value = settingsStorage.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE)
        notificationsEnabledState.value = readBoolean(NotificationPreferenceKeys.ENABLED)
        notifyBreakingState.value = readBoolean(NotificationPreferenceKeys.NOTIFY_BREAKING)
        notifyTopStoryState.value = readBoolean(NotificationPreferenceKeys.NOTIFY_TOP_STORY)
        notifyReminderState.value = readBoolean(NotificationPreferenceKeys.NOTIFY_REMINDER)
    }

    private fun readBoolean(key: String): Boolean =
        settingsStorage.getString(key, NotificationPreferenceKeys.DEFAULT_ENABLED) == "true"

    suspend fun saveNewsLanguage(languageCode: String) {
        settingsStorage.putString(KEY_NEWS_LANGUAGE, languageCode)
        newsLanguageState.value = languageCode
    }

    suspend fun saveAppLocale(localeCode: String) {
        settingsStorage.putString(KEY_APP_LOCALE, localeCode)
        appLocaleState.value = localeCode
    }

    suspend fun saveSelectedCountry(countryCode: String) {
        settingsStorage.putString(KEY_SELECTED_COUNTRY, countryCode)
        selectedCountryState.value = countryCode
    }

    /**
     * Whether the device-integrity warning has already been shown. Stored so a
     * reader on a rooted device is told once and then left alone — a warning on
     * every launch is a warning nobody reads.
     */
    fun securityWarningSeen(): Boolean =
        settingsStorage.getString(KEY_SECURITY_WARNING_SEEN, "") == "true"

    suspend fun markSecurityWarningSeen() {
        settingsStorage.putString(KEY_SECURITY_WARNING_SEEN, "true")
    }

    suspend fun saveThemeMode(mode: String) {
        settingsStorage.putString(KEY_THEME_MODE, mode)
        themeModeState.value = mode
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        settingsStorage.putString(NotificationPreferenceKeys.ENABLED, enabled.toString())
        notificationsEnabledState.value = enabled
    }

    suspend fun setNotifyBreaking(enabled: Boolean) {
        settingsStorage.putString(NotificationPreferenceKeys.NOTIFY_BREAKING, enabled.toString())
        notifyBreakingState.value = enabled
    }

    suspend fun setNotifyTopStory(enabled: Boolean) {
        settingsStorage.putString(NotificationPreferenceKeys.NOTIFY_TOP_STORY, enabled.toString())
        notifyTopStoryState.value = enabled
    }

    suspend fun setNotifyReminder(enabled: Boolean) {
        settingsStorage.putString(NotificationPreferenceKeys.NOTIFY_REMINDER, enabled.toString())
        notifyReminderState.value = enabled
    }

    /**
     * Whether the contextual notification-permission prompt has already fired.
     * Once, ever — asked again on a later launch would be the cold-start
     * request this was built to replace.
     */
    fun notificationPromptSeen(): Boolean =
        settingsStorage.getString(KEY_NOTIFICATION_PROMPT_SEEN, "") == "true"

    suspend fun markNotificationPromptSeen() {
        settingsStorage.putString(KEY_NOTIFICATION_PROMPT_SEEN, "true")
    }

    companion object {
        private const val KEY_SECURITY_WARNING_SEEN: String = "security_warning_seen"
        private const val KEY_NEWS_LANGUAGE: String = "news_language"
        private const val KEY_APP_LOCALE: String = "app_locale"
        private const val KEY_SELECTED_COUNTRY: String = "selected_country"
        private const val KEY_THEME_MODE: String = "theme_mode"
        private const val KEY_NOTIFICATION_PROMPT_SEEN: String = "notification_prompt_seen"
        private const val DEFAULT_NEWS_LANGUAGE: String = "en"
        private const val DEFAULT_APP_LOCALE: String = "en"
        private const val DEFAULT_COUNTRY: String = "us"
        private const val DEFAULT_THEME_MODE: String = "system"
    }
}

expect class SettingsStorage {
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
}
