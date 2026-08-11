package com.mk.newsshorts.data.local

import android.content.Context
import android.content.SharedPreferences

actual class SettingsStorage(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    actual fun getString(key: String, defaultValue: String): String {
        return preferences.getString(key, defaultValue) ?: defaultValue
    }

    actual fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    companion object {
        private const val PREFS_NAME: String = "news_shorts_prefs"
    }
}

