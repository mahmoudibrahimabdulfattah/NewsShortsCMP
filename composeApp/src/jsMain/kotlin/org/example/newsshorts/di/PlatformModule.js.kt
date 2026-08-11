package org.example.newsshorts.di

import org.example.newsshorts.analytics.AnalyticsReporter
import org.example.newsshorts.analytics.NoOpAnalyticsReporter
import org.example.newsshorts.data.local.SettingsStorage
import org.example.newsshorts.notifications.NoOpPushSubscriber
import org.example.newsshorts.notifications.PushSubscriber
import org.koin.dsl.module

val platformModule = module {
    single { SettingsStorage() }
    // Firebase ships for Android and iOS only; reporting and push stay off here.
    single<AnalyticsReporter> { NoOpAnalyticsReporter }
    single<PushSubscriber> { NoOpPushSubscriber }
}
