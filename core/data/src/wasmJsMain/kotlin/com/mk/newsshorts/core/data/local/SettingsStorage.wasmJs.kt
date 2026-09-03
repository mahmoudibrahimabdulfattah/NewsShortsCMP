package com.mk.newsshorts.core.data.local

actual class SettingsStorage {
    private val storage: MutableMap<String, String> = mutableMapOf()

    actual fun getString(key: String, defaultValue: String): String {
        return storage[key] ?: defaultValue
    }

    actual fun putString(key: String, value: String) {
        storage[key] = value
    }
}

