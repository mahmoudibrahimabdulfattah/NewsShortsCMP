package com.mk.newsshorts.di

import com.mk.newsshorts.core.data.local.InMemorySettingsStorage
import com.mk.newsshorts.core.data.local.SettingsStorage
import org.koin.core.Koin

actual fun platformSettingsStorage(koin: Koin): SettingsStorage {
    // wasmJs used to bind a SettingsStorage actual that was only a per-process
    // map. Binding the shared in-memory implementation gives that production
    // scratchpad its honest name: settings still do not survive a reload.
    return InMemorySettingsStorage()
}
