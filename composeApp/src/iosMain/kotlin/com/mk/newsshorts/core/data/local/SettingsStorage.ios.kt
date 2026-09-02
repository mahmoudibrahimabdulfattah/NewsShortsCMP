package com.mk.newsshorts.core.data.local

import platform.Foundation.NSUserDefaults

actual class SettingsStorage {
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String, defaultValue: String): String {
        return userDefaults.stringForKey(key) ?: defaultValue
    }

    actual fun putString(key: String, value: String) {
        userDefaults.setObject(value, forKey = key)
        userDefaults.synchronize()
    }
}

