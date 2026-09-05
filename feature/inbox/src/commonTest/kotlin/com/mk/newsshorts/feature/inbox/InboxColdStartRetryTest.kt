package com.mk.newsshorts.feature.inbox

import com.mk.newsshorts.core.contract.notifications.SentNotification
import com.mk.newsshorts.core.data.local.InMemorySettingsStorage
import com.mk.newsshorts.core.data.local.NotificationInboxStore
import com.mk.newsshorts.core.domain.notifications.NotificationInboxFeed
import com.mk.newsshorts.core.model.settings.AppPreferences
import com.mk.newsshorts.core.domain.settings.SettingsPersistence
import com.mk.newsshorts.navigation.NotificationBus
import com.mk.newsshorts.navigation.OverlayNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The unread badge exists to tell a reader about something *before* they go
 * looking. A launch that beats the network used to leave it empty for the whole
 * session: the fetch failed, the empty list stood, and the only thing that
 * fetched again was opening the inbox — so the mark appeared only after the
 * reader had already looked, which is the one thing it must not do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InboxColdStartRetryTest {

    @Test
    fun `a launch that beats the network still fills the badge`() = runTest {
        val client = FlakyInboxClient(failures = 2)
        val viewModel = viewModel(client)

        repeat(12) { runCurrent() }

        assertEquals(1, viewModel.uiState.value.unreadCount)
        assertEquals(3, client.calls, "should have retried until it succeeded")
    }

    @Test
    fun `it gives up rather than retrying for the life of the session`() = runTest {
        val client = FlakyInboxClient(failures = Int.MAX_VALUE)
        val viewModel = viewModel(client)

        repeat(12) { runCurrent() }

        assertEquals(0, viewModel.uiState.value.unreadCount)
        assertEquals(4, client.calls, "one attempt plus a bounded number of retries")
    }

    /**
     * An empty inbox is an answer, not a failure — retrying it would hammer the
     * backend for every reader who has no notifications.
     */
    @Test
    fun `an empty inbox is not retried`() = runTest {
        val client = FlakyInboxClient(failures = 0, notifications = emptyList())
        val viewModel = viewModel(client)

        repeat(12) { runCurrent() }

        assertEquals(1, client.calls)
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(
        client: NotificationInboxFeed,
    ) = InboxViewModel(
        notificationInboxClient = client,
        notificationInboxStore = NotificationInboxStore(InMemorySettingsStorage()),
        notificationBus = NotificationBus(),
        navigator = OverlayNavigator(),
        settingsManager = FixedSettings(),
        scopeOverride = backgroundScope,
        retryDelayMillis = 0L,
    )

    private class FlakyInboxClient(
        private val failures: Int,
        private val notifications: List<SentNotification> = listOf(
            SentNotification(
                sentAt = 1_000L,
                tier = "breaking",
                title = "t",
                body = "b",
                deepLink = "newsshorts://article?url=https%3A%2F%2Fexample.com%2Fa",
            ),
        ),
    ) : NotificationInboxFeed {
        var calls: Int = 0

        override suspend fun fetch(language: String): List<SentNotification>? {
            calls++
            return if (calls <= failures) null else notifications
        }
    }

    private class FixedSettings : SettingsPersistence {
        private val state = MutableStateFlow(
            AppPreferences(newsLanguage = "ar", appLocale = "ar", selectedCountry = "eg")
        )
        override val preferences: StateFlow<AppPreferences> = state.asStateFlow()
        override suspend fun saveAppLocale(localeCode: String) = Unit
        override suspend fun saveThemeMode(mode: String) = Unit
        override suspend fun saveTextScale(scale: String) = Unit
        override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setNotifyBreaking(enabled: Boolean) = Unit
        override suspend fun setNotifyTopStory(enabled: Boolean) = Unit
        override suspend fun setNotifyReminder(enabled: Boolean) = Unit
    }
}
