package com.mk.newsshorts.core.data.local

import com.mk.newsshorts.core.model.settings.AppPreferences
import com.mk.newsshorts.core.model.settings.KEY_APP_LOCALE
import com.mk.newsshorts.core.model.settings.KEY_NEWS_LANGUAGE
import com.mk.newsshorts.core.model.settings.KEY_SELECTED_COUNTRY
import com.mk.newsshorts.core.model.settings.KEY_TEXT_SCALE
import com.mk.newsshorts.core.model.settings.KEY_THEME_MODE
import com.mk.newsshorts.core.model.settings.NotificationPreferenceKeys
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every test here is a way the reader's settings could be silently discarded.
 *
 * Nothing covered this before: a `saveThemeMode` that updated the in-memory
 * flow but never reached storage passed the whole suite, and so did one that
 * reset every other preference to its default on the way. Both look correct on
 * screen until the app is restarted, which is the one moment nobody is testing
 * by hand.
 */
class SettingsManagerTest {

    /** The reader as they would be after using the app for a while. */
    private val chosen = AppPreferences(
        newsLanguage = "ar",
        appLocale = "ar",
        selectedCountry = "eg",
        themeMode = "dark",
        notificationsEnabled = false,
        notifyBreaking = false,
        notifyTopStory = true,
        notifyReminder = false,
        textScale = "large",
    )

    private fun storageWith(vararg entries: Pair<String, String>): SettingsStorage =
        InMemorySettingsStorage().apply { entries.forEach { putString(it.first, it.second) } }

    @Test
    fun `a preference the reader changed is still there after a restart`() = runTest {
        val storage = InMemorySettingsStorage()
        SettingsManager(storage).saveThemeMode("dark")

        // A second manager over the same storage is what the next launch does.
        // Asserting the flow alone would pass on a change that never persisted.
        assertEquals("dark", SettingsManager(storage).preferences.value.themeMode)
    }

    @Test
    fun `every preference reaches storage and not only the flow in front of it`() = runTest {
        val storage = InMemorySettingsStorage()
        val manager = SettingsManager(storage)

        manager.saveNewsLanguage("ar")
        manager.saveAppLocale("ar")
        manager.saveSelectedCountry("eg")
        manager.saveThemeMode("dark")
        manager.saveTextScale("large")
        manager.setNotificationsEnabled(false)
        manager.setNotifyBreaking(false)
        manager.setNotifyTopStory(true)
        manager.setNotifyReminder(false)

        assertEquals(chosen, manager.preferences.value)
        assertEquals(chosen, SettingsManager(storage).preferences.value)
    }

    @Test
    fun `changing one preference leaves the other eight alone`() = runTest {
        val storage = InMemorySettingsStorage()
        val manager = SettingsManager(storage)
        manager.apply(chosen)

        manager.saveThemeMode("light")

        assertEquals(chosen.copy(themeMode = "light"), manager.preferences.value)
        assertEquals(chosen.copy(themeMode = "light"), SettingsManager(storage).preferences.value)
    }

    @Test
    fun `a fresh manager reads what is stored rather than the defaults`() = runTest {
        // The B2 regression: sync built its payload before the stored values
        // had been read, and pushed English/US/system over the reader's own
        // choices. The constructor reads synchronously so that cannot recur.
        val manager = SettingsManager(
            storageWith(
                KEY_NEWS_LANGUAGE to "ar",
                KEY_APP_LOCALE to "ar",
                KEY_SELECTED_COUNTRY to "eg",
                KEY_THEME_MODE to "dark",
                KEY_TEXT_SCALE to "large",
                NotificationPreferenceKeys.ENABLED to "false",
                NotificationPreferenceKeys.NOTIFY_BREAKING to "false",
                NotificationPreferenceKeys.NOTIFY_TOP_STORY to "true",
                NotificationPreferenceKeys.NOTIFY_REMINDER to "false",
            )
        )

        assertEquals(chosen, manager.preferences.value)
    }

    @Test
    fun `applying a remote set replaces every preference on disk`() = runTest {
        // What sign-in does when the server has settings. A field left out of
        // the write would keep the previous account's choice.
        val storage = InMemorySettingsStorage()
        val manager = SettingsManager(storage)
        manager.apply(chosen)

        assertEquals(chosen, manager.preferences.value)
        assertEquals(chosen, SettingsManager(storage).preferences.value)
    }

    @Test
    fun `applying a legacy remote language stores the published default`() = runTest {
        val storage = InMemorySettingsStorage()
        val manager = SettingsManager(storage)

        manager.apply(chosen.copy(newsLanguage = "de"))

        assertEquals("en", manager.preferences.value.newsLanguage)
        assertEquals("en", SettingsManager(storage).preferences.value.newsLanguage)
    }

    @Test
    fun `a flag stored as anything but true reads as off`() = runTest {
        // The flags are strings on disk. A truncated or shape-changed write
        // must not read as enabled.
        val manager = SettingsManager(
            storageWith(
                NotificationPreferenceKeys.NOTIFY_BREAKING to "TRUE",
                NotificationPreferenceKeys.NOTIFY_TOP_STORY to "",
            )
        )

        assertFalse(manager.preferences.value.notifyBreaking)
        assertFalse(manager.preferences.value.notifyTopStory)
    }

    @Test
    fun `the one-time flags stay set once they are set`() = runTest {
        val storage = InMemorySettingsStorage()
        val manager = SettingsManager(storage)

        assertFalse(manager.securityWarningSeen())
        assertFalse(manager.onboardingComplete())
        assertFalse(manager.notificationPromptSeen())

        manager.markSecurityWarningSeen()
        manager.markOnboardingComplete()
        manager.markNotificationPromptSeen()

        // Read back through a new manager: a warning shown on every launch is
        // a warning nobody reads, and onboarding that reopens has no end.
        val next = SettingsManager(storage)
        assertTrue(next.securityWarningSeen())
        assertTrue(next.onboardingComplete())
        assertTrue(next.notificationPromptSeen())
    }

    @Test
    fun `no chosen categories reads as none rather than one blank`() = runTest {
        val storage = InMemorySettingsStorage()
        val manager = SettingsManager(storage)

        assertEquals(emptyList(), manager.preferredCategories())

        manager.savePreferredCategories(listOf("general", "technology"))
        assertEquals(listOf("general", "technology"), SettingsManager(storage).preferredCategories())

        // Skipping onboarding writes an empty string; splitting it naively
        // yields a single blank category that matches no feed.
        manager.savePreferredCategories(emptyList())
        assertEquals(emptyList(), SettingsManager(storage).preferredCategories())
    }
}
