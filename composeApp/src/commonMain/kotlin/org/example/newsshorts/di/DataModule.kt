package org.example.newsshorts.di

import org.example.newsshorts.data.local.NewsLocalDataSource
import org.example.newsshorts.data.local.SettingsManager
import org.example.newsshorts.data.remote.NewsApiClient
import org.example.newsshorts.data.remote.createHttpClient
import org.example.newsshorts.data.repository.NewsRepositoryImpl
import org.example.newsshorts.domain.repository.NewsRepository
import org.example.newsshorts.navigation.DeepLinkBus
import org.koin.dsl.module

val dataModule = module {
    // Not lazy: a cold-start deep link is posted before anything else resolves.
    single { DeepLinkBus() }
    single(createdAtStart = false) { createHttpClient() }
    single(createdAtStart = false) { NewsApiClient(httpClient = get()) }
    single(createdAtStart = false) { NewsLocalDataSource(settingsStorage = get()) }
    single(createdAtStart = false) { SettingsManager(settingsStorage = get()) }
    single<NewsRepository>(createdAtStart = false) {
        NewsRepositoryImpl(
            newsApiClient = get(),
            localDataSource = get()
        )
    }
}

