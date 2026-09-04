package com.mk.newsshorts.feature.inbox.di

import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.feature.inbox.InboxViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

val inboxModule = module {
    viewModel {
        InboxViewModel(
            notificationInboxClient = get(),
            notificationInboxStore = get(),
            notificationBus = get(),
            navigator = get(),
            settingsManager = get<SettingsManager>(),
        )
    }
}
