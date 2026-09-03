package com.mk.newsshorts.core.data.local

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

class LocalStorageSettingsStorage : SettingsStorage {
    override fun getString(key: String, defaultValue: String): String {
        return localStorage[key] ?: defaultValue
    }

    override fun putString(key: String, value: String) {
        localStorage[key] = value
    }
}
