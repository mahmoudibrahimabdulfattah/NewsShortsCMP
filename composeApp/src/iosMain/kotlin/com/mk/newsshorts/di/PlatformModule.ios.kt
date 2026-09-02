package com.mk.newsshorts.di

import com.mk.newsshorts.core.domain.analytics.AnalyticsReporter
import com.mk.newsshorts.core.domain.analytics.NoOpAnalyticsReporter
import com.mk.newsshorts.core.data.local.SettingsStorage
import com.mk.newsshorts.core.domain.notifications.NoOpPushSubscriber
import com.mk.newsshorts.core.domain.auth.AuthClient
import com.mk.newsshorts.core.domain.auth.NoOpAuthClient
import com.mk.newsshorts.core.domain.notifications.PushSubscriber
import com.mk.newsshorts.core.domain.security.DeviceIntegrityInspector
import com.mk.newsshorts.core.domain.security.PermissiveDeviceIntegrityInspector
import com.mk.newsshorts.core.domain.sync.NoOpRemoteSyncClient
import com.mk.newsshorts.core.domain.sync.RemoteSyncClient
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
