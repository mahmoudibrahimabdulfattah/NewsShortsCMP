package com.mk.newsshorts.core.data.local

import android.content.Context
import android.content.SharedPreferences

class AndroidSettingsStorage(context: Context) : SettingsStorage {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun getString(key: String, defaultValue: String): String {
        return preferences.getString(key, defaultValue) ?: defaultValue
    }

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    companion object {
        internal const val PREFS_NAME: String = "news_shorts_prefs"
    }
}
