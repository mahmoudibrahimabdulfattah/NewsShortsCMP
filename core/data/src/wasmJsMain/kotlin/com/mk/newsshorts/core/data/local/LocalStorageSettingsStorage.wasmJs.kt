package com.mk.newsshorts.core.data.local

import kotlinx.browser.localStorage

/**
 * The same browser storage the JS target uses, so a reader's settings survive a
 * reload on wasm too.
 *
 * Separate from the JS class only because the two targets do not share a source
 * set; `localStorage` here comes from the wasm DOM bindings, where the indexed
 * accessors the JS version uses are not available.
 */
class LocalStorageSettingsStorage : SettingsStorage {
    override fun getString(key: String, defaultValue: String): String =
        localStorage.getItem(key) ?: defaultValue

    override fun putString(key: String, value: String) {
        localStorage.setItem(key, value)
    }
}
