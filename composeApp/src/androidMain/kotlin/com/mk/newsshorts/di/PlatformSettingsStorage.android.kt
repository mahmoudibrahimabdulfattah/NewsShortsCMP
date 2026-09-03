package com.mk.newsshorts.di

import android.content.Context
import com.mk.newsshorts.core.data.local.AndroidSettingsStorage
import com.mk.newsshorts.core.data.local.SettingsStorage
import org.koin.core.Koin

actual fun platformSettingsStorage(koin: Koin): SettingsStorage =
    AndroidSettingsStorage(context = koin.get<Context>())
