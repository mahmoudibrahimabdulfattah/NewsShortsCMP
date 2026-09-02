package com.mk.newsshorts.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The one-time flag the security gate reads. Separate from the reader's own
 * preferences: nobody chose it, and only the gate has any business with it.
 */
interface SecurityFlagPersistence {
    fun securityWarningSeen(): Boolean
    suspend fun markSecurityWarningSeen()
}

/**
 * What onboarding needs to remember once and then stop asking about. The
 * category list belongs here rather than with the preferences flow because
 * onboarding writes it only when it finishes.
 */
interface OnboardingPersistence {
    fun onboardingComplete(): Boolean
    suspend fun markOnboardingComplete()
    fun notificationPromptSeen(): Boolean
    suspend fun markNotificationPromptSeen()
    fun preferredCategories(): List<String>
    suspend fun savePreferredCategories(apiValues: List<String>)
}

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
) : SettingsPersistence, SecurityFlagPersistence, OnboardingPersistence {
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

    suspend fun apply(preferences: AppPreferences) {
        settingsStorage.putString(KEY_NEWS_LANGUAGE, preferences.newsLanguage)
        settingsStorage.putString(KEY_APP_LOCALE, preferences.appLocale)
        settingsStorage.putString(KEY_SELECTED_COUNTRY, preferences.selectedCountry)
        settingsStorage.putString(KEY_THEME_MODE, preferences.themeMode)
        settingsStorage.putString(KEY_TEXT_SCALE, preferences.textScale)
        settingsStorage.putString(NotificationPreferenceKeys.ENABLED, preferences.notificationsEnabled.toString())
        settingsStorage.putString(NotificationPreferenceKeys.NOTIFY_BREAKING, preferences.notifyBreaking.toString())
        settingsStorage.putString(NotificationPreferenceKeys.NOTIFY_TOP_STORY, preferences.notifyTopStory.toString())
        settingsStorage.putString(NotificationPreferenceKeys.NOTIFY_REMINDER, preferences.notifyReminder.toString())
        preferencesState.value = preferences
    }

    /**
     * Whether the device-integrity warning has already been shown. Stored so a
     * reader on a rooted device is told once and then left alone — a warning on
     * every launch is a warning nobody reads.
     */
    override fun securityWarningSeen(): Boolean =
        settingsStorage.getString(KEY_SECURITY_WARNING_SEEN, "") == "true"

    override suspend fun markSecurityWarningSeen() {
        settingsStorage.putString(KEY_SECURITY_WARNING_SEEN, "true")
    }

    /**
     * Whether the contextual notification-permission prompt has already fired.
     * Once, ever — asked again on a later launch would be the cold-start
     * request this was built to replace.
     */
    override fun notificationPromptSeen(): Boolean =
        settingsStorage.getString(KEY_NOTIFICATION_PROMPT_SEEN, "") == "true"

    override suspend fun markNotificationPromptSeen() {
        settingsStorage.putString(KEY_NOTIFICATION_PROMPT_SEEN, "true")
    }

    /**
     * Whether onboarding has run. Read once at start rather than exposed as a
     * flow: it answers a question asked exactly once per install, and a flow
     * would let the finished flow re-open itself the moment it wrote its own
     * completion.
     */
    override fun onboardingComplete(): Boolean =
        settingsStorage.getString(KEY_ONBOARDING_COMPLETE, "") == "true"

    override suspend fun markOnboardingComplete() {
        settingsStorage.putString(KEY_ONBOARDING_COMPLETE, "true")
    }

    /**
     * The categories the reader said they wanted, in the order they matter.
     * Empty means they skipped or picked none, which is not the same as
     * wanting nothing — see `orderedCategories`.
     */
    override fun preferredCategories(): List<String> =
        settingsStorage.getString(KEY_PREFERRED_CATEGORIES, "")
            .split(',')
            .filter { it.isNotBlank() }

    override suspend fun savePreferredCategories(apiValues: List<String>) {
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
