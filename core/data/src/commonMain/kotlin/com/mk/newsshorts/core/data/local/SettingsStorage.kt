package com.mk.newsshorts.core.data.local

interface SettingsStorage {
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
}

class InMemorySettingsStorage : SettingsStorage {
    private val storage: MutableMap<String, String> = mutableMapOf()

    override fun getString(key: String, defaultValue: String): String {
        return storage[key] ?: defaultValue
    }

    override fun putString(key: String, value: String) {
        storage[key] = value
    }
}
