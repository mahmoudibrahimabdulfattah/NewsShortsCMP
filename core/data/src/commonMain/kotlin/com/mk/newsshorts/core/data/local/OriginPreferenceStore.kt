package com.mk.newsshorts.core.data.local

import com.mk.newsshorts.core.domain.OriginPreferenceStore

/** Keeps a recovered origin across launches so an outage delays only one call. */
class SettingsOriginPreferenceStore(
    private val settingsStorage: SettingsStorage,
) : OriginPreferenceStore {
    override fun preferredOrigin(): String? =
        settingsStorage.getString(KEY_PREFERRED_BACKEND_ORIGIN, "")
            .takeIf(String::isNotBlank)

    override fun savePreferredOrigin(origin: String) {
        settingsStorage.putString(KEY_PREFERRED_BACKEND_ORIGIN, origin)
    }

    private companion object {
        const val KEY_PREFERRED_BACKEND_ORIGIN: String = "preferred_backend_origin"
    }
}
