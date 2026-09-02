package com.mk.newsshorts.feature.settings

import com.mk.newsshorts.core.model.analytics.AnalyticsEvent
import com.mk.newsshorts.core.domain.analytics.AnalyticsReporter
import com.mk.newsshorts.core.model.settings.AppPreferences
import com.mk.newsshorts.core.domain.settings.SettingsPersistence
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.getStrings
import com.mk.newsshorts.presentation.mvi.TextScale
import com.mk.newsshorts.presentation.mvi.ThemeMode
import com.mk.newsshorts.presentation.viewmodel.BaseViewModel
import com.mk.newsshorts.core.domain.sync.SyncPublisher
import com.mk.newsshorts.core.model.sync.SyncedSettings
import com.mk.newsshorts.core.model.sync.toSyncedSettings
import kotlinx.coroutines.CoroutineScope
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
    data object ToggleNotifications : SettingsUiEvent
    data class ToggleNotificationTier(val tier: NotificationTier) : SettingsUiEvent
}

sealed interface SettingsUiEffect {
    data class ShowToast(val message: String) : SettingsUiEffect
    data object RequestNotificationPermission : SettingsUiEffect
}

class SettingsViewModel(
    private val settingsManager: SettingsPersistence,
    private val analytics: AnalyticsReporter,
    private val syncPublisher: SyncPublisher,
    private val scopeOverride: CoroutineScope? = null,
) : BaseViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    private val mutableEffect = MutableSharedFlow<SettingsUiEffect>()
    val uiEffect: SharedFlow<SettingsUiEffect> = mutableEffect.asSharedFlow()

    private val settingsScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    init {
        // Seeded from the store rather than collected from it. Every path that
        // changes a setting goes through this class — a handler, or applySynced
        // for the remote-wins case — and each already updates the state as it
        // writes, so a collector would only be re-deriving what is about to be
        // set anyway. It would also be a coroutine that never completes, which
        // forces every caller to hand over a scope it is willing to leave
        // running forever, tests included.
        mutableState.value = settingsManager.preferences.value.toUiState()
    }

    internal fun currentSyncedSettings(): SyncedSettings =
        settingsManager.preferences.value.toSyncedSettings()

    /** True only when the event changed a synced preference. */
    fun processEvent(event: SettingsUiEvent): Boolean = when (event) {
        is SettingsUiEvent.SelectAppLocale -> selectAppLocale(event.locale)
        is SettingsUiEvent.SelectThemeMode -> selectThemeMode(event.mode)
        is SettingsUiEvent.SelectTextScale -> selectTextScale(event.scale)
        SettingsUiEvent.ToggleNotifications -> toggleNotifications()
        is SettingsUiEvent.ToggleNotificationTier -> toggleNotificationTier(event.tier)
    }

    private fun selectAppLocale(locale: AppLocale): Boolean {
        if (locale == mutableState.value.appLocale) return false
        mutableState.update { it.copy(appLocale = locale) }
        settingsScope.launch {
            analytics.logEvent(AnalyticsEvent.AppLanguageChanged(locale.code))
            analytics.setProperty("app_language", locale.code)
            settingsManager.saveAppLocale(locale.code)
            publishSettings()
            val strings = getStrings(locale)
            val languageName = strings.languageNames[locale.code] ?: locale.displayName
            mutableEffect.emit(SettingsUiEffect.ShowToast("${strings.languageChangedTo} $languageName"))
        }
        return true
    }

    private fun selectThemeMode(mode: ThemeMode): Boolean {
        if (mode == mutableState.value.themeMode) return false
        mutableState.update { it.copy(themeMode = mode) }
        settingsScope.launch {
            settingsManager.saveThemeMode(mode.name.lowercase())
            publishSettings()
        }
        return true
    }

    private fun selectTextScale(scale: TextScale): Boolean {
        if (scale == mutableState.value.textScale) return false
        mutableState.update { it.copy(textScale = scale) }
        settingsScope.launch {
            settingsManager.saveTextScale(scale.stored)
            publishSettings()
        }
        return true
    }

    private fun toggleNotifications(): Boolean {
        val enabling = !mutableState.value.notificationsEnabled
        mutableState.update { it.copy(notificationsEnabled = enabling) }
        settingsScope.launch {
            settingsManager.setNotificationsEnabled(enabling)
            publishSettings()
            if (enabling) {
                mutableEffect.emit(SettingsUiEffect.RequestNotificationPermission)
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
                settingsScope.launch {
                    settingsManager.setNotifyBreaking(enabling)
                    publishSettings()
                }
            }
            NotificationTier.TOP_STORY -> {
                val enabling = !state.notifyTopStory
                mutableState.update { it.copy(notifyTopStory = enabling) }
                settingsScope.launch {
                    settingsManager.setNotifyTopStory(enabling)
                    publishSettings()
                }
            }
            NotificationTier.REMINDER -> {
                val enabling = !state.notifyReminder
                mutableState.update { it.copy(notifyReminder = enabling) }
                settingsScope.launch {
                    settingsManager.setNotifyReminder(enabling)
                    publishSettings()
                }
            }
        }
        return true
    }

    private fun publishSettings() {
        syncPublisher.publishSettings(currentSyncedSettings())
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
