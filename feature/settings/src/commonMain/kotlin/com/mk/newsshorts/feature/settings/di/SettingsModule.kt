package com.mk.newsshorts.feature.settings.di

import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.feature.settings.SettingsViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
    viewModel {
        SettingsViewModel(
            settingsManager = get<SettingsManager>(),
            analytics = get(),
            syncPublisher = get(),
        )
    }
}
