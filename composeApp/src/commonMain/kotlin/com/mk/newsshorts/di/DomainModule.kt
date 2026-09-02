package com.mk.newsshorts.di

import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.core.domain.use_case.DeleteAccountUseCase
import com.mk.newsshorts.core.domain.use_case.GetTopHeadlinesUseCase
import com.mk.newsshorts.core.domain.search.SearchNewsUseCase
import com.mk.newsshorts.core.domain.search.SearchNews
import com.mk.newsshorts.core.domain.notifications.PushSubscriptionSynchronizer
import com.mk.newsshorts.core.domain.sync.AccountSyncUseCase
import com.mk.newsshorts.core.domain.sync.DefaultSyncPublisher
import com.mk.newsshorts.core.domain.sync.SyncPublisher
import org.koin.dsl.module

val domainModule = module {
    factory { GetTopHeadlinesUseCase(newsRepository = get()) }
    factory { DeleteAccountUseCase(authClient = get(), remoteSyncClient = get()) }
    factory<SearchNews> { SearchNewsUseCase(newsRepository = get()) }
    single<SyncPublisher>(createdAtStart = false) {
        DefaultSyncPublisher(authSession = get(), remoteSyncClient = get())
    }
    single(createdAtStart = true) {
        PushSubscriptionSynchronizer(settingsManager = get<SettingsManager>(), pushSubscriber = get())
    }
    factory {
        AccountSyncUseCase(
            remoteSyncClient = get(),
            savedArticles = get(),
            settingsManager = get<SettingsManager>(),
            syncPublisher = get(),
            authSession = get(),
        )
    }
}
