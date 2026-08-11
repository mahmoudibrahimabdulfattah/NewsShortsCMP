package com.mk.newsshorts.di

import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.analytics.createAnalyticsReporter
import com.mk.newsshorts.data.local.SettingsStorage
import com.mk.newsshorts.notifications.FirebasePushSubscriber
import com.mk.newsshorts.notifications.PushSubscriber
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformModule = module {
    single { SettingsStorage(context = androidContext()) }
    single<AnalyticsReporter> { createAnalyticsReporter(androidContext()) }
    single<PushSubscriber> { FirebasePushSubscriber(androidContext()) }
}
