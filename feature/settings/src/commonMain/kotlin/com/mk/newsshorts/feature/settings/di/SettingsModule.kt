package com.mk.newsshorts.feature.settings.di

import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.feature.settings.SettingsViewModel
import org.koin.dsl.module

val settingsModule = module {
    single(createdAtStart = false) {
        SettingsViewModel(
            settingsManager = get<SettingsManager>(),
            analytics = get(),
            syncPublisher = get(),
        )
    }
}
