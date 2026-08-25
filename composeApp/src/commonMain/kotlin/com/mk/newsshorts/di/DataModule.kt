package com.mk.newsshorts.di

import com.mk.newsshorts.data.local.NewsLocalDataSource
import com.mk.newsshorts.data.local.NotificationInboxStore
import com.mk.newsshorts.data.local.SavedArticlesStore
import com.mk.newsshorts.data.local.PendingSignInEmailStore
import com.mk.newsshorts.data.local.RecentSearchesStore
import com.mk.newsshorts.data.local.SeenArticlesStore
import com.mk.newsshorts.data.local.SettingsManager
import com.mk.newsshorts.data.remote.RemoteConfigClient
import com.mk.newsshorts.data.remote.NewsApiClient
import com.mk.newsshorts.data.remote.NotificationInboxClient
import com.mk.newsshorts.data.remote.SharePageResolver
import com.mk.newsshorts.data.remote.createHttpClient
import com.mk.newsshorts.data.repository.NewsRepositoryImpl
import com.mk.newsshorts.data.repository.SavedArticlesRepository
import com.mk.newsshorts.domain.repository.NewsRepository
import com.mk.newsshorts.navigation.DeepLinkBus
import com.mk.newsshorts.navigation.NotificationBus
import com.mk.newsshorts.navigation.SignInLinkBus
import org.koin.dsl.module

val dataModule = module {
    // Not lazy: a cold-start deep link is posted before anything else resolves.
    single { DeepLinkBus() }
    single { NotificationBus() }
    single { SignInLinkBus() }
    single(createdAtStart = false) { createHttpClient() }
    single(createdAtStart = false) { NewsApiClient(httpClient = get()) }
    single(createdAtStart = false) { RemoteConfigClient(httpClient = get()) }
    single(createdAtStart = false) { SharePageResolver(httpClient = get()) }
    single(createdAtStart = false) { NotificationInboxClient(httpClient = get()) }
    single(createdAtStart = false) { NewsLocalDataSource(settingsStorage = get()) }
    single(createdAtStart = false) { SettingsManager(settingsStorage = get()) }
    single(createdAtStart = false) { SavedArticlesStore(settingsStorage = get()) }
    single(createdAtStart = false) { SavedArticlesRepository(store = get<SavedArticlesStore>()) }
    single(createdAtStart = false) { SeenArticlesStore(settingsStorage = get()) }
    single(createdAtStart = false) { NotificationInboxStore(settingsStorage = get()) }
    single(createdAtStart = false) { RecentSearchesStore(settingsStorage = get()) }
    single(createdAtStart = false) { PendingSignInEmailStore(settingsStorage = get()) }
    single<NewsRepository>(createdAtStart = false) {
        NewsRepositoryImpl(
            newsApiClient = get(),
            localDataSource = get()
        )
    }
}

