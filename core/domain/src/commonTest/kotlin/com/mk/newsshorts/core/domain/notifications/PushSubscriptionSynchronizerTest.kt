package com.mk.newsshorts.core.domain.notifications

import com.mk.newsshorts.core.domain.notifications.PushSubscriber
import com.mk.newsshorts.core.model.settings.AppPreferences
import com.mk.newsshorts.core.domain.settings.SettingsPersistence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PushSubscriptionSynchronizerTest {
    @Test
    fun `enabled preferences subscribe to the stored news language`() = runTest {
        val settings = RecordingSettingsPersistence(
            AppPreferences(newsLanguage = "ar", notificationsEnabled = true),
        )
        val pushSubscriber = RecordingPushSubscriber()

        PushSubscriptionSynchronizer(settings, pushSubscriber, backgroundScope)
        runCurrent()

        assertEquals(listOf("ar"), pushSubscriber.subscribedLanguages)

        settings.update { it.copy(newsLanguage = "en") }
        runCurrent()

        assertEquals(listOf("ar", "en"), pushSubscriber.subscribedLanguages)
    }

    @Test
    fun `disabled preferences unsubscribe instead of subscribing`() = runTest {
        val settings = RecordingSettingsPersistence(
            AppPreferences(newsLanguage = "ar", notificationsEnabled = false),
        )
        val pushSubscriber = RecordingPushSubscriber()

        PushSubscriptionSynchronizer(settings, pushSubscriber, backgroundScope)
        runCurrent()

        assertEquals(1, pushSubscriber.unsubscribeCount)
        assertEquals(emptyList(), pushSubscriber.subscribedLanguages)
    }

    @Test
    fun `a legacy language subscribes to the published default topic`() = runTest {
        val settings = RecordingSettingsPersistence(
            AppPreferences(newsLanguage = "de", notificationsEnabled = true),
        )
        val pushSubscriber = RecordingPushSubscriber()

        PushSubscriptionSynchronizer(settings, pushSubscriber, backgroundScope)
        runCurrent()

        assertEquals(listOf("en"), pushSubscriber.subscribedLanguages)
    }

    private class RecordingSettingsPersistence(
        initial: AppPreferences,
    ) : SettingsPersistence {
        private val mutablePreferences = MutableStateFlow(initial)
        override val preferences: StateFlow<AppPreferences> = mutablePreferences.asStateFlow()

        fun update(transform: (AppPreferences) -> AppPreferences) {
            mutablePreferences.update(transform)
        }

        override suspend fun saveAppLocale(localeCode: String) = Unit
        override suspend fun saveThemeMode(mode: String) = Unit
        override suspend fun saveTextScale(scale: String) = Unit
        override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setNotifyBreaking(enabled: Boolean) = Unit
        override suspend fun setNotifyTopStory(enabled: Boolean) = Unit
        override suspend fun setNotifyReminder(enabled: Boolean) = Unit
    }

    private class RecordingPushSubscriber : PushSubscriber {
        val subscribedLanguages = mutableListOf<String>()
        var unsubscribeCount = 0

        override fun subscribeToLanguage(language: String) {
            subscribedLanguages += language
        }

        override fun unsubscribeAll() {
            unsubscribeCount += 1
        }
    }
}
