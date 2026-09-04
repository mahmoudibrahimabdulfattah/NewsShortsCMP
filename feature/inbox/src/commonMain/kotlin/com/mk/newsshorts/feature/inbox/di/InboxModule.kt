package com.mk.newsshorts.feature.inbox.di

import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.feature.inbox.InboxViewModel
import org.koin.dsl.module

val inboxModule = module {
    single(createdAtStart = false) {
        InboxViewModel(
            notificationInboxClient = get(),
            notificationInboxStore = get(),
            notificationBus = get(),
            navigator = get(),
            settingsManager = get<SettingsManager>(),
        )
    }
}
