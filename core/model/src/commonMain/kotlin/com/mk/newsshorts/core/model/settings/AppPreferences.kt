package com.mk.newsshorts.core.model.settings

import com.mk.newsshorts.core.model.FeedLanguage
import com.mk.newsshorts.core.model.feed.CountryOption
import com.mk.newsshorts.core.model.locale.DeviceLocale
import com.mk.newsshorts.core.model.locale.currentDeviceLocale

/**
 * Every synced preference, as one value.
 *
 * They used to be nine independent `MutableStateFlow`s. Nothing read more than
 * one at a time, so nothing tore in practice — but the moment something needs
 * all of them at once, which sign-in sync does, nine flows are nine chances to
 * catch the set half-updated. One value cannot be half-updated.
 *
 * Stored as the raw strings that go to disk rather than the app's enums: this
 * is the storage layer's shape. Missing first-run values are seeded from the
 * device, resolved through the model enums before any feed path can observe
 * them.
 */
data class AppPreferences(
    val newsLanguage: String,
    val appLocale: String,
    val selectedCountry: String,
    val themeMode: String = DEFAULT_THEME_MODE,
    val notificationsEnabled: Boolean = true,
    val notifyBreaking: Boolean = true,
    val notifyTopStory: Boolean = true,
    val notifyReminder: Boolean = true,
    val textScale: String = DEFAULT_TEXT_SCALE,
)

/**
 * Takes the reader rather than a storage implementation, so decoding can be
 * tested on every target without a backing store.
 */
fun readAppPreferences(
    getString: (key: String, fallback: String) -> String,
    deviceLocale: DeviceLocale? = null,
): AppPreferences {
    var defaults: AppPreferences? = null
    fun defaults(): AppPreferences =
        defaults ?: defaultAppPreferences(deviceLocale ?: currentDeviceLocale())
            .also { defaults = it }
    fun storedOrDefault(key: String, fallback: (AppPreferences) -> String): String {
        val stored = getString(key, MISSING_PREFERENCE)
        return if (stored == MISSING_PREFERENCE) fallback(defaults()) else stored
    }
    fun flag(key: String): Boolean =
        getString(key, NotificationPreferenceKeys.DEFAULT_ENABLED) == "true"

    return AppPreferences(
        newsLanguage = storedOrDefault(KEY_NEWS_LANGUAGE) { it.newsLanguage },
        appLocale = storedOrDefault(KEY_APP_LOCALE) { it.appLocale },
        selectedCountry = storedOrDefault(KEY_SELECTED_COUNTRY) { it.selectedCountry },
        themeMode = getString(KEY_THEME_MODE, DEFAULT_THEME_MODE),
        notificationsEnabled = flag(NotificationPreferenceKeys.ENABLED),
        notifyBreaking = flag(NotificationPreferenceKeys.NOTIFY_BREAKING),
        notifyTopStory = flag(NotificationPreferenceKeys.NOTIFY_TOP_STORY),
        notifyReminder = flag(NotificationPreferenceKeys.NOTIFY_REMINDER),
        textScale = getString(KEY_TEXT_SCALE, DEFAULT_TEXT_SCALE),
    )
}

private fun defaultAppPreferences(deviceLocale: DeviceLocale): AppPreferences {
    val language = deviceLocale.languageTag
        .substringBefore('-')
        .substringBefore('_')
        .lowercase()
    val country = deviceLocale.region
        ?.takeIf { it.isNotBlank() }
        ?.lowercase()
        ?.let { region -> CountryOption.entries.find { it.code == region } }
        ?: CountryOption.UNITED_STATES

    return AppPreferences(
        newsLanguage = FeedLanguage.resolve(language),
        appLocale = AppLocale.fromCode(language).code,
        selectedCountry = country.code,
    )
}

const val KEY_NEWS_LANGUAGE: String = "news_language"
const val KEY_APP_LOCALE: String = "app_locale"
const val KEY_SELECTED_COUNTRY: String = "selected_country"
const val KEY_THEME_MODE: String = "theme_mode"
const val KEY_TEXT_SCALE: String = "text_scale"

const val DEFAULT_THEME_MODE: String = "system"
const val DEFAULT_TEXT_SCALE: String = "default"

private const val MISSING_PREFERENCE: String = "\u0000__news_shorts_missing_preference__"
