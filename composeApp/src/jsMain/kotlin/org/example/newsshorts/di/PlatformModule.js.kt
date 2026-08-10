package org.example.newsshorts.di

import org.example.newsshorts.analytics.AnalyticsReporter
import org.example.newsshorts.analytics.NoOpAnalyticsReporter
import org.example.newsshorts.data.local.SettingsStorage
import org.koin.dsl.module

val platformModule = module {
    single { SettingsStorage() }
    // Firebase ships for Android and iOS only; reporting stays off here.
    single<AnalyticsReporter> { NoOpAnalyticsReporter }
}
