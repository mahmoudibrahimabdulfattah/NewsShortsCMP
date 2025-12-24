package org.example.newsshorts.di

import org.example.newsshorts.data.local.NewsLocalDataSource
import org.example.newsshorts.data.local.SettingsManager
import org.example.newsshorts.data.remote.NewsApiClient
import org.example.newsshorts.data.remote.createHttpClient
import org.example.newsshorts.data.repository.NewsRepositoryImpl
import org.example.newsshorts.domain.repository.NewsRepository
import org.koin.dsl.module

val dataModule = module {
    single { createHttpClient() }
    single { NewsApiClient(httpClient = get()) }
    single { NewsLocalDataSource() }
    single { SettingsManager(settingsStorage = get()) }
    single<NewsRepository> {
        NewsRepositoryImpl(
            newsApiClient = get(),
            localDataSource = get()
        )
    }
}

