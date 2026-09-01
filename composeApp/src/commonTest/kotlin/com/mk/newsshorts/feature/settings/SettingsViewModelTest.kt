package com.mk.newsshorts.feature.settings

import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.data.local.AppPreferences
import com.mk.newsshorts.data.local.SettingsPersistence
import com.mk.newsshorts.notifications.PushSubscriber
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.mvi.NotificationTier
import com.mk.newsshorts.presentation.mvi.TextScale
import com.mk.newsshorts.presentation.mvi.ThemeMode
import com.mk.newsshorts.sync.SyncedSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Test
    fun `every settings choice reaches the screen and survives on disk`() = runTest {
        val stored = AppPreferences(
            newsLanguage = "ar",
            appLocale = "en",
            selectedCountry = "eg",
            themeMode = "system",
            notificationsEnabled = false,
            notifyBreaking = true,
            notifyTopStory = true,
            notifyReminder = true,
            textScale = "default",
        )
        val persistence = RecordingSettingsPersistence(stored)
        val push = RecordingPushSubscriber()
        val viewModel = viewModel(persistence, push)
        viewModel.applyStored(stored)

        viewModel.processEvent(SettingsUiEvent.SelectAppLocale(AppLocale.ARABIC))
        viewModel.processEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
        viewModel.processEvent(SettingsUiEvent.SelectTextScale(TextScale.LARGE))
        viewModel.processEvent(SettingsUiEvent.ToggleNotifications("ar"))
        viewModel.processEvent(SettingsUiEvent.ToggleNotificationTier(NotificationTier.BREAKING))
        viewModel.processEvent(SettingsUiEvent.ToggleNotificationTier(NotificationTier.TOP_STORY))
        viewModel.processEvent(SettingsUiEvent.ToggleNotificationTier(NotificationTier.REMINDER))
        advanceUntilIdle()

        assertEquals(
            SettingsUiState(
                appLocale = AppLocale.ARABIC,
                themeMode = ThemeMode.DARK,
                textScale = TextScale.LARGE,
                notificationsEnabled = true,
                notifyBreaking = false,
                notifyTopStory = false,
                notifyReminder = false,
            ),
            viewModel.uiState.value,
        )
        assertEquals(
            stored.copy(
                appLocale = "ar",
                themeMode = "dark",
                textScale = "large",
                notificationsEnabled = true,
                notifyBreaking = false,
                notifyTopStory = false,
                notifyReminder = false,
            ),
            persistence.preferences.value,
        )
        assertEquals(listOf("ar"), push.subscribedLanguages)
    }

    @Test
    fun `changing one setting does not put the others back to defaults`() = runTest {
        val stored = AppPreferences(
            newsLanguage = "ar",
            appLocale = "ar",
            selectedCountry = "eg",
            themeMode = "dark",
            notificationsEnabled = false,
            notifyBreaking = false,
            notifyTopStory = false,
            notifyReminder = false,
            textScale = "large",
        )
        val persistence = RecordingSettingsPersistence(stored)
        val viewModel = viewModel(persistence)
        viewModel.applyStored(stored)

        viewModel.processEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.LIGHT))
        advanceUntilIdle()

        assertEquals(
            SettingsUiState(
                appLocale = AppLocale.ARABIC,
                themeMode = ThemeMode.LIGHT,
                textScale = TextScale.LARGE,
                notificationsEnabled = false,
                notifyBreaking = false,
                notifyTopStory = false,
                notifyReminder = false,
            ),
            viewModel.uiState.value,
        )
        assertEquals(stored.copy(themeMode = "light"), persistence.preferences.value)
    }

    @Test
    fun `account sync sends the reader's stored choices instead of defaults`() = runTest {
        val stored = AppPreferences(
            newsLanguage = "ar",
            appLocale = "ar",
            selectedCountry = "eg",
            themeMode = "dark",
            notificationsEnabled = false,
            notifyBreaking = false,
            notifyTopStory = true,
            notifyReminder = false,
            textScale = "extra_large",
        )
        val viewModel = viewModel(RecordingSettingsPersistence(stored))

        assertEquals(
            SyncedSettings(
                newsLanguage = "ar",
                appLocale = "ar",
                selectedCountry = "eg",
                themeMode = "dark",
                notificationsEnabled = false,
                notifyBreaking = false,
                notifyTopStory = true,
                notifyReminder = false,
            ),
            viewModel.currentSyncedSettings(),
        )
    }

    private fun TestScope.viewModel(
        persistence: RecordingSettingsPersistence,
        pushSubscriber: RecordingPushSubscriber = RecordingPushSubscriber(),
    ): SettingsViewModel = SettingsViewModel(
        settingsManager = persistence,
        analytics = RecordingAnalytics(),
        pushSubscriber = pushSubscriber,
        scopeOverride = this,
    )

    private class RecordingSettingsPersistence(
        initial: AppPreferences,
    ) : SettingsPersistence {
        private val mutablePreferences = MutableStateFlow(initial)
        override val preferences: StateFlow<AppPreferences> = mutablePreferences.asStateFlow()

        override suspend fun saveAppLocale(localeCode: String) {
            mutablePreferences.update { it.copy(appLocale = localeCode) }
        }

        override suspend fun saveThemeMode(mode: String) {
            mutablePreferences.update { it.copy(themeMode = mode) }
        }

        override suspend fun saveTextScale(scale: String) {
            mutablePreferences.update { it.copy(textScale = scale) }
        }

        override suspend fun setNotificationsEnabled(enabled: Boolean) {
            mutablePreferences.update { it.copy(notificationsEnabled = enabled) }
        }

        override suspend fun setNotifyBreaking(enabled: Boolean) {
            mutablePreferences.update { it.copy(notifyBreaking = enabled) }
        }

        override suspend fun setNotifyTopStory(enabled: Boolean) {
            mutablePreferences.update { it.copy(notifyTopStory = enabled) }
        }

        override suspend fun setNotifyReminder(enabled: Boolean) {
            mutablePreferences.update { it.copy(notifyReminder = enabled) }
        }
    }

    private class RecordingPushSubscriber : PushSubscriber {
        val subscribedLanguages = mutableListOf<String>()

        override fun subscribeToLanguage(language: String) {
            subscribedLanguages += language
        }

        override fun unsubscribeAll() = Unit
    }

    private class RecordingAnalytics : AnalyticsReporter {
        override fun logEvent(event: AnalyticsEvent) = Unit
        override fun setProperty(name: String, value: String) = Unit
        override fun recordError(message: String, cause: Throwable?) = Unit
    }
}
