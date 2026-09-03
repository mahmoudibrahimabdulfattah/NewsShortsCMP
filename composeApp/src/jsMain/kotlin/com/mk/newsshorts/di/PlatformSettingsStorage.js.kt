package com.mk.newsshorts.di

import com.mk.newsshorts.core.data.local.LocalStorageSettingsStorage
import com.mk.newsshorts.core.data.local.SettingsStorage
import org.koin.core.Koin

actual fun platformSettingsStorage(koin: Koin): SettingsStorage =
    LocalStorageSettingsStorage()
