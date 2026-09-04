package com.mk.newsshorts.di

import com.mk.newsshorts.core.data.local.LocalStorageSettingsStorage
import com.mk.newsshorts.core.data.local.SettingsStorage
import org.koin.core.Koin

actual fun platformSettingsStorage(koin: Koin): SettingsStorage {
    // Was an in-memory map, so nothing a reader chose survived a reload. Uses
    // the browser's localStorage now, like the JS target.
    return LocalStorageSettingsStorage()
}
