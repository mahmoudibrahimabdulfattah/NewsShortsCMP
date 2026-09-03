package com.mk.newsshorts.di

import com.mk.newsshorts.core.data.local.SettingsStorage
import org.koin.core.Koin

expect fun platformSettingsStorage(koin: Koin): SettingsStorage
