package com.mk.newsshorts.core.model.settings

import com.mk.newsshorts.core.model.locale.DeviceLocale
import com.mk.newsshorts.core.model.sync.toSyncedSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * These preferences are what sign-in sync pushes to the server. Reading them
 * wrong does not fail — it quietly replaces the reader's choices with
 * somebody's idea of a default.
 */
class AppPreferencesTest {

    private val englishUs = DeviceLocale(languageTag = "en-US", region = "us")

    /** Stands in for the real settings store without needing a platform backing store. */
    private fun storage(vararg entries: Pair<String, String>): (String, String) -> String {
        val map = entries.toMap()
        return { key, fallback -> map[key] ?: fallback }
    }

    private fun preferences(
        vararg entries: Pair<String, String>,
        deviceLocale: DeviceLocale = englishUs,
    ): AppPreferences = readAppPreferences(storage(*entries), deviceLocale)

    @Test
    fun `an Arabic Egypt device gets Arabic Egypt defaults`() {
        val prefs = preferences(
            deviceLocale = DeviceLocale(languageTag = "ar-EG", region = "eg"),
        )

        assertEquals("ar", prefs.newsLanguage)
        assertEquals("ar", prefs.appLocale)
        assertEquals("eg", prefs.selectedCountry)
        assertEquals("system", prefs.themeMode)
        assertEquals("default", prefs.textScale)
        assertTrue(prefs.notificationsEnabled)
        assertTrue(prefs.notifyBreaking)
        assertTrue(prefs.notifyTopStory)
        assertTrue(prefs.notifyReminder)
    }

    @Test
    fun `an English US device keeps its supported region`() {
        val prefs = preferences()

        assertEquals("en", prefs.newsLanguage)
        assertEquals("en", prefs.appLocale)
        assertEquals("us", prefs.selectedCountry)
    }

    @Test
    fun `a device in an unavailable region falls back to Egypt`() {
        val prefs = preferences(
            deviceLocale = DeviceLocale(languageTag = "en-NZ", region = "nz"),
        )

        assertEquals("en", prefs.newsLanguage)
        assertEquals("en", prefs.appLocale)
        assertEquals("eg", prefs.selectedCountry)
    }

    @Test
    fun `a French France device falls back languages and keeps its supported region`() {
        val prefs = preferences(
            deviceLocale = DeviceLocale(languageTag = "fr-FR", region = "fr"),
        )

        assertEquals("en", prefs.newsLanguage)
        assertEquals("en", prefs.appLocale)
        assertEquals("fr", prefs.selectedCountry)
    }

    @Test
    fun `a legacy news language resolves to the published default`() {
        val prefs = preferences(
            KEY_NEWS_LANGUAGE to "fr",
            deviceLocale = DeviceLocale(languageTag = "ar-EG", region = "eg"),
        )

        assertEquals("en", prefs.newsLanguage)
    }

    @Test
    fun `stored values win over every default`() {
        val prefs = preferences(
            KEY_NEWS_LANGUAGE to "en",
            KEY_APP_LOCALE to "en",
            KEY_SELECTED_COUNTRY to "sa",
            KEY_THEME_MODE to "dark",
            KEY_TEXT_SCALE to "large",
            NotificationPreferenceKeys.ENABLED to "false",
            NotificationPreferenceKeys.NOTIFY_BREAKING to "false",
            NotificationPreferenceKeys.NOTIFY_TOP_STORY to "true",
            NotificationPreferenceKeys.NOTIFY_REMINDER to "false",
            deviceLocale = DeviceLocale(languageTag = "ar-EG", region = "eg"),
        )

        assertEquals("en", prefs.newsLanguage)
        assertEquals("en", prefs.appLocale)
        assertEquals("sa", prefs.selectedCountry)
        assertEquals("dark", prefs.themeMode)
        assertEquals("large", prefs.textScale)
        assertFalse(prefs.notificationsEnabled)
        assertFalse(prefs.notifyBreaking)
        assertTrue(prefs.notifyTopStory)
        assertFalse(prefs.notifyReminder)
    }

    @Test
    fun `anything that is not the string true is false`() {
        // The flags are stored as "true"/"false" strings, so a truncated or
        // shape-changed write must not read as enabled.
        val prefs = preferences(NotificationPreferenceKeys.NOTIFY_BREAKING to "TRUE")

        assertFalse(prefs.notifyBreaking)
    }

    @Test
    fun `the synced shape carries every account preference and no device ones`() {
        val prefs = AppPreferences(
            newsLanguage = "ar",
            appLocale = "ar",
            selectedCountry = "sa",
            themeMode = "dark",
            notificationsEnabled = false,
            notifyBreaking = false,
            notifyTopStory = true,
            notifyReminder = false,
            textScale = "large",
        )

        val synced = prefs.toSyncedSettings()

        assertEquals("ar", synced.newsLanguage)
        assertEquals("ar", synced.appLocale)
        assertEquals("sa", synced.selectedCountry)
        assertEquals("dark", synced.themeMode)
        assertFalse(synced.notificationsEnabled)
        assertFalse(synced.notifyBreaking)
        assertTrue(synced.notifyTopStory)
        assertFalse(synced.notifyReminder)
    }

    @Test
    fun `sync pushes a reader's real preferences instead of the defaults`() {
        // The regression this phase exists for: sync used to build its payload
        // from the UI state, which holds hardcoded defaults until
        // loadSavedSettings finishes. A sign-in landing first pushed English,
        // US and "system" over the reader's actual choices.
        val stored = preferences(
            KEY_NEWS_LANGUAGE to "ar",
            KEY_SELECTED_COUNTRY to "eg",
            KEY_THEME_MODE to "dark",
        )

        val synced = stored.toSyncedSettings()

        assertEquals("ar", synced.newsLanguage)
        assertEquals("eg", synced.selectedCountry)
        assertEquals("dark", synced.themeMode)
    }

    @Test
    fun `changing one preference leaves the rest of the snapshot alone`() {
        val stored = preferences(KEY_NEWS_LANGUAGE to "ar", KEY_THEME_MODE to "dark")

        val afterThemeChange = stored.copy(themeMode = "light")

        assertEquals("ar", afterThemeChange.newsLanguage)
        assertEquals("light", afterThemeChange.themeMode)
    }
}
