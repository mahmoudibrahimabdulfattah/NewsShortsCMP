package com.mk.newsshorts.di

import com.mk.newsshorts.BuildConfig
import com.mk.newsshorts.core.domain.analytics.AnalyticsReporter
import com.mk.newsshorts.analytics.createAnalyticsReporter
import com.mk.newsshorts.core.domain.auth.AuthClient
import com.mk.newsshorts.auth.createAuthClient
import com.mk.newsshorts.core.data.local.AndroidSettingsStorage
import com.mk.newsshorts.core.data.local.SettingsStorage
import com.mk.newsshorts.notifications.FirebasePushSubscriber
import com.mk.newsshorts.core.domain.notifications.PushSubscriber
import com.mk.newsshorts.security.AndroidDeviceIntegrityInspector
import com.mk.newsshorts.core.domain.security.DeviceIntegrityInspector
import com.mk.newsshorts.core.domain.sync.RemoteSyncClient
import com.mk.newsshorts.core.data.sync.createRemoteSyncClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidPlatformModule = module {
    single<SettingsStorage> { AndroidSettingsStorage(context = androidContext()) }
    single<AnalyticsReporter> { createAnalyticsReporter(androidContext()) }
    single<PushSubscriber> { FirebasePushSubscriber(androidContext()) }
    single<DeviceIntegrityInspector> {
        AndroidDeviceIntegrityInspector(
            context = androidContext(),
            expectedSigningSha256 = BuildConfig.EXPECTED_SIGNING_SHA256,
            isDebug = BuildConfig.DEBUG,
        )
    }
    single<AuthClient> { createAuthClient(androidContext()) }
    single<RemoteSyncClient> { createRemoteSyncClient(androidContext()) }
}
