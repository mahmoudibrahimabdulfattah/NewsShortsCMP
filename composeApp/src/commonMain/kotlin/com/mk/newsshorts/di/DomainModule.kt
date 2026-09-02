package com.mk.newsshorts.di

import com.mk.newsshorts.data.local.SettingsManager
import com.mk.newsshorts.domain.use_case.GetTopHeadlinesUseCase
import com.mk.newsshorts.feature.search.SearchNewsUseCase
import com.mk.newsshorts.feature.search.SearchNews
import com.mk.newsshorts.notifications.PushSubscriptionSynchronizer
import com.mk.newsshorts.sync.AccountSyncUseCase
import com.mk.newsshorts.sync.DefaultSyncPublisher
import com.mk.newsshorts.sync.SyncPublisher
import org.koin.dsl.module

val domainModule = module {
    factory { GetTopHeadlinesUseCase(newsRepository = get()) }
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
