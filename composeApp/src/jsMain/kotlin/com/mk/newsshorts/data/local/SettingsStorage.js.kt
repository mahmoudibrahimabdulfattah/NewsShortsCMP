package com.mk.newsshorts.data.local

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

actual class SettingsStorage {
    actual fun getString(key: String, defaultValue: String): String {
        return localStorage[key] ?: defaultValue
    }

    actual fun putString(key: String, value: String) {
        localStorage[key] = value
    }
}

