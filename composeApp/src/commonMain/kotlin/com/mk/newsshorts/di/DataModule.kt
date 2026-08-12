package com.mk.newsshorts.di

import com.mk.newsshorts.data.local.NewsLocalDataSource
import com.mk.newsshorts.data.local.SavedArticlesStore
import com.mk.newsshorts.data.local.SeenArticlesStore
import com.mk.newsshorts.data.local.SettingsManager
import com.mk.newsshorts.data.remote.RemoteConfigClient
import com.mk.newsshorts.data.remote.NewsApiClient
import com.mk.newsshorts.data.remote.createHttpClient
import com.mk.newsshorts.data.repository.NewsRepositoryImpl
import com.mk.newsshorts.domain.repository.NewsRepository
import com.mk.newsshorts.navigation.DeepLinkBus
import org.koin.dsl.module

val dataModule = module {
    // Not lazy: a cold-start deep link is posted before anything else resolves.
    single { DeepLinkBus() }
    single(createdAtStart = false) { createHttpClient() }
    single(createdAtStart = false) { NewsApiClient(httpClient = get()) }
    single(createdAtStart = false) { RemoteConfigClient(httpClient = get()) }
    single(createdAtStart = false) { NewsLocalDataSource(settingsStorage = get()) }
    single(createdAtStart = false) { SettingsManager(settingsStorage = get()) }
    single(createdAtStart = false) { SavedArticlesStore(settingsStorage = get()) }
    single(createdAtStart = false) { SeenArticlesStore(settingsStorage = get()) }
    single<NewsRepository>(createdAtStart = false) {
        NewsRepositoryImpl(
            newsApiClient = get(),
            localDataSource = get()
        )
    }
}

