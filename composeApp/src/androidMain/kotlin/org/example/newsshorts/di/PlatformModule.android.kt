package org.example.newsshorts.di

import org.example.newsshorts.analytics.AnalyticsReporter
import org.example.newsshorts.analytics.createAnalyticsReporter
import org.example.newsshorts.data.local.SettingsStorage
import org.example.newsshorts.notifications.FirebasePushSubscriber
import org.example.newsshorts.notifications.PushSubscriber
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformModule = module {
    single { SettingsStorage(context = androidContext()) }
    single<AnalyticsReporter> { createAnalyticsReporter(androidContext()) }
    single<PushSubscriber> { FirebasePushSubscriber(androidContext()) }
}
