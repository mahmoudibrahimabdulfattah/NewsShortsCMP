package com.mk.newsshorts.di

import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.analytics.NoOpAnalyticsReporter
import com.mk.newsshorts.data.local.SettingsStorage
import com.mk.newsshorts.notifications.NoOpPushSubscriber
import com.mk.newsshorts.auth.AuthClient
import com.mk.newsshorts.auth.NoOpAuthClient
import com.mk.newsshorts.notifications.PushSubscriber
import com.mk.newsshorts.security.DeviceIntegrityInspector
import com.mk.newsshorts.security.PermissiveDeviceIntegrityInspector
import com.mk.newsshorts.sync.NoOpRemoteSyncClient
import com.mk.newsshorts.sync.RemoteSyncClient
import org.koin.dsl.module

val platformModule = module {
    single { SettingsStorage() }
    // Firebase ships for Android and iOS only; reporting and push stay off here.
    single<AnalyticsReporter> { NoOpAnalyticsReporter }
    single<PushSubscriber> { NoOpPushSubscriber }
    // Root and repackaging checks are an Android concern; nothing to inspect here.
    single<DeviceIntegrityInspector> { PermissiveDeviceIntegrityInspector }
    // Firebase Auth and Firestore ship for Android only.
    single<AuthClient> { NoOpAuthClient }
    single<RemoteSyncClient> { NoOpRemoteSyncClient }
}
