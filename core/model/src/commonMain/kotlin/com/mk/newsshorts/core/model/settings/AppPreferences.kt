package com.mk.newsshorts.core.model.settings

import com.mk.newsshorts.core.model.FeedLanguage

/**
 * Every synced preference, as one value.
 *
 * They used to be nine independent `MutableStateFlow`s. Nothing read more than
 * one at a time, so nothing tore in practice — but the moment something needs
 * all of them at once, which sign-in sync does, nine flows are nine chances to
 * catch the set half-updated. One value cannot be half-updated.
 *
 * Stored as strings rather than the app's enums: this is the storage layer's
 * shape. Legacy news-language values are normalized as they enter this shape,
 * before any feed or notification path can observe them.
 */
data class AppPreferences(
    val newsLanguage: String = DEFAULT_NEWS_LANGUAGE,
    val appLocale: String = DEFAULT_APP_LOCALE,
    val selectedCountry: String = DEFAULT_COUNTRY,
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
fun readAppPreferences(getString: (key: String, fallback: String) -> String): AppPreferences {
    fun flag(key: String): Boolean =
        getString(key, NotificationPreferenceKeys.DEFAULT_ENABLED) == "true"

    return AppPreferences(
        newsLanguage = FeedLanguage.resolve(getString(KEY_NEWS_LANGUAGE, DEFAULT_NEWS_LANGUAGE)),
        appLocale = getString(KEY_APP_LOCALE, DEFAULT_APP_LOCALE),
        selectedCountry = getString(KEY_SELECTED_COUNTRY, DEFAULT_COUNTRY),
        themeMode = getString(KEY_THEME_MODE, DEFAULT_THEME_MODE),
        notificationsEnabled = flag(NotificationPreferenceKeys.ENABLED),
        notifyBreaking = flag(NotificationPreferenceKeys.NOTIFY_BREAKING),
        notifyTopStory = flag(NotificationPreferenceKeys.NOTIFY_TOP_STORY),
        notifyReminder = flag(NotificationPreferenceKeys.NOTIFY_REMINDER),
        textScale = getString(KEY_TEXT_SCALE, DEFAULT_TEXT_SCALE),
    )
}

const val KEY_NEWS_LANGUAGE: String = "news_language"
const val KEY_APP_LOCALE: String = "app_locale"
const val KEY_SELECTED_COUNTRY: String = "selected_country"
const val KEY_THEME_MODE: String = "theme_mode"
const val KEY_TEXT_SCALE: String = "text_scale"

const val DEFAULT_NEWS_LANGUAGE: String = FeedLanguage.DEFAULT
const val DEFAULT_APP_LOCALE: String = "en"
const val DEFAULT_COUNTRY: String = "gb"
const val DEFAULT_THEME_MODE: String = "system"
const val DEFAULT_TEXT_SCALE: String = "default"
