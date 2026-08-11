package com.mk.newsshorts.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class SettingsManager(
    private val settingsStorage: SettingsStorage
) {
    private val newsLanguageState: MutableStateFlow<String> = MutableStateFlow(DEFAULT_NEWS_LANGUAGE)
    private val appLocaleState: MutableStateFlow<String> = MutableStateFlow(DEFAULT_APP_LOCALE)
    private val selectedCountryState: MutableStateFlow<String> = MutableStateFlow(DEFAULT_COUNTRY)

    val newsLanguageFlow: Flow<String> = newsLanguageState.asStateFlow()
    val appLocaleFlow: Flow<String> = appLocaleState.asStateFlow()
    val selectedCountryFlow: Flow<String> = selectedCountryState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        newsLanguageState.value = settingsStorage.getString(KEY_NEWS_LANGUAGE, DEFAULT_NEWS_LANGUAGE)
        appLocaleState.value = settingsStorage.getString(KEY_APP_LOCALE, DEFAULT_APP_LOCALE)
        selectedCountryState.value = settingsStorage.getString(KEY_SELECTED_COUNTRY, DEFAULT_COUNTRY)
    }

    suspend fun saveNewsLanguage(languageCode: String) {
        settingsStorage.putString(KEY_NEWS_LANGUAGE, languageCode)
        newsLanguageState.value = languageCode
    }

    suspend fun saveAppLocale(localeCode: String) {
        settingsStorage.putString(KEY_APP_LOCALE, localeCode)
        appLocaleState.value = localeCode
    }

    suspend fun saveSelectedCountry(countryCode: String) {
        settingsStorage.putString(KEY_SELECTED_COUNTRY, countryCode)
        selectedCountryState.value = countryCode
    }

    companion object {
        private const val KEY_NEWS_LANGUAGE: String = "news_language"
        private const val KEY_APP_LOCALE: String = "app_locale"
        private const val KEY_SELECTED_COUNTRY: String = "selected_country"
        private const val DEFAULT_NEWS_LANGUAGE: String = "en"
        private const val DEFAULT_APP_LOCALE: String = "en"
        private const val DEFAULT_COUNTRY: String = "us"
    }
}

expect class SettingsStorage {
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
}
