package com.mk.newsshorts.feature.settings

import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.data.local.AppPreferences
import com.mk.newsshorts.data.local.SettingsPersistence
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.mvi.NotificationTier
import com.mk.newsshorts.presentation.mvi.TextScale
import com.mk.newsshorts.presentation.mvi.ThemeMode
import com.mk.newsshorts.sync.SyncPublisher
import com.mk.newsshorts.sync.SyncedSettings
import com.mk.newsshorts.sync.toSyncedSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
        val syncPublisher = RecordingSyncPublisher()
        val viewModel = viewModel(persistence, syncPublisher)

        viewModel.processEvent(SettingsUiEvent.SelectAppLocale(AppLocale.ARABIC))
        viewModel.processEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
        viewModel.processEvent(SettingsUiEvent.SelectTextScale(TextScale.LARGE))
        viewModel.processEvent(SettingsUiEvent.ToggleNotifications)
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
        assertEquals(
            stored.copy(
                appLocale = "ar",
                themeMode = "dark",
                textScale = "large",
                notificationsEnabled = true,
                notifyBreaking = false,
                notifyTopStory = false,
                notifyReminder = false,
            ).toSyncedSettings(),
            syncPublisher.publishedSettings.last(),
        )
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
        syncPublisher: RecordingSyncPublisher = RecordingSyncPublisher(),
    ): SettingsViewModel = SettingsViewModel(
        settingsManager = persistence,
        analytics = RecordingAnalytics(),
        syncPublisher = syncPublisher,
        scopeOverride = this,
    ).also { runCurrent() }

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

    private class RecordingAnalytics : AnalyticsReporter {
        override fun logEvent(event: AnalyticsEvent) = Unit
        override fun setProperty(name: String, value: String) = Unit
        override fun recordError(message: String, cause: Throwable?) = Unit
    }

    private class RecordingSyncPublisher : SyncPublisher {
        val publishedSettings = mutableListOf<SyncedSettings>()

        override fun publishSavedArticles(articles: List<NewsArticle>) = Unit

        override suspend fun publishSavedArticlesNow(articles: List<NewsArticle>) = Unit

        override fun publishSettings(settings: SyncedSettings) {
            publishedSettings += settings
        }

        override suspend fun publishSettingsNow(settings: SyncedSettings) {
            publishSettings(settings)
        }

        override fun discardQueued() = Unit
    }
}
