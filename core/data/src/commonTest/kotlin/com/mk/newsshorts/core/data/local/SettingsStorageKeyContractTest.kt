package com.mk.newsshorts.core.data.local

import com.mk.newsshorts.core.model.settings.KEY_APP_LOCALE
import com.mk.newsshorts.core.model.settings.KEY_NEWS_LANGUAGE
import com.mk.newsshorts.core.model.settings.KEY_SELECTED_COUNTRY
import com.mk.newsshorts.core.model.settings.KEY_TEXT_SCALE
import com.mk.newsshorts.core.model.settings.KEY_THEME_MODE
import com.mk.newsshorts.core.model.settings.NotificationPreferenceKeys
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * These strings are a persisted-data contract with every installed copy of the
 * app. Changing one silently discards whatever the reader had chosen or saved,
 * because the app starts reading a different key from the same backing store.
 */
class SettingsStorageKeyContractTest {

    @Test
    fun `app preference keys stay stable`() {
        assertEquals("news_language", KEY_NEWS_LANGUAGE)
        assertEquals("app_locale", KEY_APP_LOCALE)
        assertEquals("selected_country", KEY_SELECTED_COUNTRY)
        assertEquals("theme_mode", KEY_THEME_MODE)
        assertEquals("text_scale", KEY_TEXT_SCALE)
    }

    @Test
    fun `notification preference keys stay stable`() {
        assertEquals("notifications_enabled", NotificationPreferenceKeys.ENABLED)
        assertEquals("notify_breaking", NotificationPreferenceKeys.NOTIFY_BREAKING)
        assertEquals("notify_top_story", NotificationPreferenceKeys.NOTIFY_TOP_STORY)
        assertEquals("notify_reminder", NotificationPreferenceKeys.NOTIFY_REMINDER)
    }

    @Test
    fun `settings manager private keys stay stable`() = runTest {
        val storage = RecordingSettingsStorage()
        val manager = SettingsManager(storage)

        manager.markSecurityWarningSeen()
        manager.markNotificationPromptSeen()
        manager.markOnboardingComplete()
        manager.savePreferredCategories(listOf("general", "technology"))

        assertEquals("true", storage.values["security_warning_seen"])
        assertEquals("true", storage.values["notification_prompt_seen"])
        assertEquals("true", storage.values["onboarding_complete"])
        assertEquals("general,technology", storage.values["preferred_categories"])
    }

    @Test
    fun `local store keys stay stable`() {
        RecordingSettingsStorage().also { storage ->
            SettingsOriginPreferenceStore(storage).savePreferredOrigin("https://origin.example")
            assertEquals("https://origin.example", storage.values["preferred_backend_origin"])
        }
        RecordingSettingsStorage().also { storage ->
            RecentSearchesStore(storage).add("gaza")
            assertTrue("recent_searches" in storage.values)
        }
        RecordingSettingsStorage().also { storage ->
            SavedArticlesStore(storage).save(emptyList())
            assertEquals("[]", storage.values["saved_articles"])
        }
        RecordingSettingsStorage().also { storage ->
            PendingSignInEmailStore(storage).save("reader@example.com")
            assertEquals("reader@example.com", storage.values["pending_sign_in_email"])
        }
        RecordingSettingsStorage().also { storage ->
            SeenArticlesStore(storage).markSeen("https://example.com/story")
            assertTrue("seen_articles" in storage.values)
        }
        RecordingSettingsStorage().also { storage ->
            val store = NotificationInboxStore(storage)
            store.markRead("https://example.com/story")
            store.dismiss("https://example.com/story")
            assertTrue("notification_inbox_read" in storage.values)
            assertTrue("notification_inbox_dismissed" in storage.values)
        }
    }

    @Test
    fun `news cache storage keys stay stable`() {
        assertEquals("news_cache_", NewsLocalDataSource.CACHE_PREFIX)
        assertEquals("news_cache_index", NewsLocalDataSource.cacheIndexKey(1))
        assertEquals("news_cache_index_v2", NewsLocalDataSource.cacheIndexKey(2))
        assertEquals("v2_language_ar_sports", NewsLocalDataSource.createCacheKey("language", "ar_sports"))
        RecordingSettingsStorage().also { storage ->
            NewsLocalDataSource(storage).clearCache()
            assertEquals("", storage.values["news_cache_index_v2"])
        }
    }

    private class RecordingSettingsStorage(
        initialValues: Map<String, String> = emptyMap()
    ) : SettingsStorage {
        val values: MutableMap<String, String> = initialValues.toMutableMap()

        override fun getString(key: String, defaultValue: String): String {
            return values[key] ?: defaultValue
        }

        override fun putString(key: String, value: String) {
            values[key] = value
        }
    }
}
