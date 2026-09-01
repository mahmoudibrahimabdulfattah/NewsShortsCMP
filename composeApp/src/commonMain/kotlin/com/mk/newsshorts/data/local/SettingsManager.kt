package com.mk.newsshorts.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

class SettingsManager(
    private val settingsStorage: SettingsStorage
) : SettingsPersistence {
    private val preferencesState: MutableStateFlow<AppPreferences> =
        MutableStateFlow(readAppPreferences(settingsStorage::getString))

    /**
     * One snapshot rather than nine flows. Read synchronously in the
     * constructor, so by the time anything can ask, these are the reader's real
     * preferences and not the defaults — which is what sign-in sync depends on.
     */
    override val preferences: StateFlow<AppPreferences> = preferencesState.asStateFlow()

    suspend fun saveNewsLanguage(languageCode: String) {
        settingsStorage.putString(KEY_NEWS_LANGUAGE, languageCode)
        preferencesState.update { it.copy(newsLanguage = languageCode) }
    }

    override suspend fun saveAppLocale(localeCode: String) {
        settingsStorage.putString(KEY_APP_LOCALE, localeCode)
        preferencesState.update { it.copy(appLocale = localeCode) }
    }

    suspend fun saveSelectedCountry(countryCode: String) {
        settingsStorage.putString(KEY_SELECTED_COUNTRY, countryCode)
        preferencesState.update { it.copy(selectedCountry = countryCode) }
    }

    override suspend fun saveThemeMode(mode: String) {
        settingsStorage.putString(KEY_THEME_MODE, mode)
        preferencesState.update { it.copy(themeMode = mode) }
    }

    override suspend fun saveTextScale(scale: String) {
        settingsStorage.putString(KEY_TEXT_SCALE, scale)
        preferencesState.update { it.copy(textScale = scale) }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        settingsStorage.putString(NotificationPreferenceKeys.ENABLED, enabled.toString())
        preferencesState.update { it.copy(notificationsEnabled = enabled) }
    }

    override suspend fun setNotifyBreaking(enabled: Boolean) {
        settingsStorage.putString(NotificationPreferenceKeys.NOTIFY_BREAKING, enabled.toString())
        preferencesState.update { it.copy(notifyBreaking = enabled) }
    }

    override suspend fun setNotifyTopStory(enabled: Boolean) {
        settingsStorage.putString(NotificationPreferenceKeys.NOTIFY_TOP_STORY, enabled.toString())
        preferencesState.update { it.copy(notifyTopStory = enabled) }
    }

    override suspend fun setNotifyReminder(enabled: Boolean) {
        settingsStorage.putString(NotificationPreferenceKeys.NOTIFY_REMINDER, enabled.toString())
        preferencesState.update { it.copy(notifyReminder = enabled) }
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

    /**
     * Whether onboarding has run. Read once at start rather than exposed as a
     * flow: it answers a question asked exactly once per install, and a flow
     * would let the finished flow re-open itself the moment it wrote its own
     * completion.
     */
    fun onboardingComplete(): Boolean =
        settingsStorage.getString(KEY_ONBOARDING_COMPLETE, "") == "true"

    suspend fun markOnboardingComplete() {
        settingsStorage.putString(KEY_ONBOARDING_COMPLETE, "true")
    }

    /**
     * The categories the reader said they wanted, in the order they matter.
     * Empty means they skipped or picked none, which is not the same as
     * wanting nothing — see `orderedCategories`.
     */
    fun preferredCategories(): List<String> =
        settingsStorage.getString(KEY_PREFERRED_CATEGORIES, "")
            .split(',')
            .filter { it.isNotBlank() }

    suspend fun savePreferredCategories(apiValues: List<String>) {
        settingsStorage.putString(KEY_PREFERRED_CATEGORIES, apiValues.joinToString(","))
    }

    companion object {
        private const val KEY_SECURITY_WARNING_SEEN: String = "security_warning_seen"
        private const val KEY_NOTIFICATION_PROMPT_SEEN: String = "notification_prompt_seen"
        private const val KEY_ONBOARDING_COMPLETE: String = "onboarding_complete"
        private const val KEY_PREFERRED_CATEGORIES: String = "preferred_categories"
    }
}

expect class SettingsStorage {
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
}
