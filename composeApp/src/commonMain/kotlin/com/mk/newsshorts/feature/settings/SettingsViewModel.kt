package com.mk.newsshorts.feature.settings

import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.data.local.AppPreferences
import com.mk.newsshorts.data.local.SettingsManager
import com.mk.newsshorts.domain.model.FeedLanguage
import com.mk.newsshorts.notifications.PushSubscriber
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.getStrings
import com.mk.newsshorts.presentation.mvi.NotificationTier
import com.mk.newsshorts.presentation.mvi.TextScale
import com.mk.newsshorts.presentation.mvi.ThemeMode
import com.mk.newsshorts.presentation.viewmodel.BaseViewModel
import com.mk.newsshorts.sync.SyncedSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val appLocale: AppLocale = AppLocale.ENGLISH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textScale: TextScale = TextScale.DEFAULT,
    val notificationsEnabled: Boolean = true,
    val notifyBreaking: Boolean = true,
    val notifyTopStory: Boolean = true,
    val notifyReminder: Boolean = true,
)

sealed interface SettingsUiEvent {
    data class SelectAppLocale(val locale: AppLocale) : SettingsUiEvent
    data class SelectThemeMode(val mode: ThemeMode) : SettingsUiEvent
    data class SelectTextScale(val scale: TextScale) : SettingsUiEvent
    data class ToggleNotifications(val newsLanguage: String) : SettingsUiEvent
    data class ToggleNotificationTier(val tier: NotificationTier) : SettingsUiEvent
}

sealed interface SettingsUiEffect {
    data class ShowToast(val message: String) : SettingsUiEffect
    data object RequestNotificationPermission : SettingsUiEffect
}

class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val analytics: AnalyticsReporter,
    private val pushSubscriber: PushSubscriber,
) : BaseViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    private val mutableEffect = MutableSharedFlow<SettingsUiEffect>()
    val uiEffect: SharedFlow<SettingsUiEffect> = mutableEffect.asSharedFlow()

    fun applyStored(preferences: AppPreferences) {
        mutableState.value = preferences.toUiState()
    }

    suspend fun applySynced(settings: SyncedSettings) {
        val locale = AppLocale.fromCode(settings.appLocale)
        val theme = ThemeMode.entries.find {
            it.name.equals(settings.themeMode, ignoreCase = true)
        } ?: mutableState.value.themeMode
        settingsManager.saveAppLocale(locale.code)
        settingsManager.saveThemeMode(theme.name.lowercase())
        settingsManager.setNotificationsEnabled(settings.notificationsEnabled)
        settingsManager.setNotifyBreaking(settings.notifyBreaking)
        settingsManager.setNotifyTopStory(settings.notifyTopStory)
        settingsManager.setNotifyReminder(settings.notifyReminder)
        mutableState.update {
            it.copy(
                appLocale = locale,
                themeMode = theme,
                notificationsEnabled = settings.notificationsEnabled,
                notifyBreaking = settings.notifyBreaking,
                notifyTopStory = settings.notifyTopStory,
                notifyReminder = settings.notifyReminder,
            )
        }
        if (settings.notificationsEnabled) {
            pushSubscriber.subscribeToLanguage(FeedLanguage.resolve(settings.newsLanguage))
        } else {
            pushSubscriber.unsubscribeAll()
        }
    }

    /** True only when the event changed a synced preference. */
    fun processEvent(event: SettingsUiEvent): Boolean = when (event) {
        is SettingsUiEvent.SelectAppLocale -> selectAppLocale(event.locale)
        is SettingsUiEvent.SelectThemeMode -> selectThemeMode(event.mode)
        is SettingsUiEvent.SelectTextScale -> selectTextScale(event.scale)
        is SettingsUiEvent.ToggleNotifications -> toggleNotifications(event.newsLanguage)
        is SettingsUiEvent.ToggleNotificationTier -> toggleNotificationTier(event.tier)
    }

    private fun selectAppLocale(locale: AppLocale): Boolean {
        if (locale == mutableState.value.appLocale) return false
        mutableState.update { it.copy(appLocale = locale) }
        viewModelScope.launch {
            analytics.logEvent(AnalyticsEvent.AppLanguageChanged(locale.code))
            analytics.setProperty("app_language", locale.code)
            settingsManager.saveAppLocale(locale.code)
            val strings = getStrings(locale)
            val languageName = strings.languageNames[locale.code] ?: locale.displayName
            mutableEffect.emit(SettingsUiEffect.ShowToast("${strings.languageChangedTo} $languageName"))
        }
        return true
    }

    private fun selectThemeMode(mode: ThemeMode): Boolean {
        if (mode == mutableState.value.themeMode) return false
        mutableState.update { it.copy(themeMode = mode) }
        viewModelScope.launch { settingsManager.saveThemeMode(mode.name.lowercase()) }
        return true
    }

    private fun selectTextScale(scale: TextScale): Boolean {
        if (scale == mutableState.value.textScale) return false
        mutableState.update { it.copy(textScale = scale) }
        viewModelScope.launch { settingsManager.saveTextScale(scale.stored) }
        return true
    }

    private fun toggleNotifications(newsLanguage: String): Boolean {
        val enabling = !mutableState.value.notificationsEnabled
        mutableState.update { it.copy(notificationsEnabled = enabling) }
        viewModelScope.launch {
            settingsManager.setNotificationsEnabled(enabling)
            if (enabling) {
                pushSubscriber.subscribeToLanguage(FeedLanguage.resolve(newsLanguage))
                mutableEffect.emit(SettingsUiEffect.RequestNotificationPermission)
            } else {
                pushSubscriber.unsubscribeAll()
            }
        }
        return true
    }

    private fun toggleNotificationTier(tier: NotificationTier): Boolean {
        val state = mutableState.value
        when (tier) {
            NotificationTier.BREAKING -> {
                val enabling = !state.notifyBreaking
                mutableState.update { it.copy(notifyBreaking = enabling) }
                viewModelScope.launch { settingsManager.setNotifyBreaking(enabling) }
            }
            NotificationTier.TOP_STORY -> {
                val enabling = !state.notifyTopStory
                mutableState.update { it.copy(notifyTopStory = enabling) }
                viewModelScope.launch { settingsManager.setNotifyTopStory(enabling) }
            }
            NotificationTier.REMINDER -> {
                val enabling = !state.notifyReminder
                mutableState.update { it.copy(notifyReminder = enabling) }
                viewModelScope.launch { settingsManager.setNotifyReminder(enabling) }
            }
        }
        return true
    }
}

private fun AppPreferences.toUiState(): SettingsUiState = SettingsUiState(
    appLocale = AppLocale.fromCode(appLocale),
    themeMode = ThemeMode.entries.find { it.name.equals(themeMode, ignoreCase = true) }
        ?: ThemeMode.SYSTEM,
    textScale = TextScale.fromStored(textScale),
    notificationsEnabled = notificationsEnabled,
    notifyBreaking = notifyBreaking,
    notifyTopStory = notifyTopStory,
    notifyReminder = notifyReminder,
)
