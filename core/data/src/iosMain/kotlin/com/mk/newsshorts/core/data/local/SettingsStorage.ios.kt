package com.mk.newsshorts.core.data.local

import platform.Foundation.NSUserDefaults

class NsUserDefaultsSettingsStorage : SettingsStorage {
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String, defaultValue: String): String {
        return userDefaults.stringForKey(key) ?: defaultValue
    }

    override fun putString(key: String, value: String) {
        userDefaults.setObject(value, forKey = key)
        userDefaults.synchronize()
    }
}
