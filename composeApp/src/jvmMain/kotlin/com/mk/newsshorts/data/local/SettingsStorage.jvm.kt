package com.mk.newsshorts.data.local

import java.util.prefs.Preferences

actual class SettingsStorage {
    private val preferences: Preferences = Preferences.userNodeForPackage(SettingsStorage::class.java)

    actual fun getString(key: String, defaultValue: String): String {
        return preferences.get(key, defaultValue)
    }

    actual fun putString(key: String, value: String) {
        preferences.put(key, value)
        preferences.flush()
    }
}

