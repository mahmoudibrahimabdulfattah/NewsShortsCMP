package com.mk.newsshorts.di

import com.mk.newsshorts.core.domain.auth.AuthSession
import com.mk.newsshorts.core.domain.auth.DefaultAuthSession
import com.mk.newsshorts.core.data.local.NewsLocalDataSource
import com.mk.newsshorts.core.data.local.NotificationInboxStore
import com.mk.newsshorts.core.data.local.SavedArticlesStore
import com.mk.newsshorts.core.data.local.PendingSignInEmailPersistence
import com.mk.newsshorts.core.data.local.PendingSignInEmailStore
import com.mk.newsshorts.core.data.local.RecentSearchesStore
import com.mk.newsshorts.core.domain.search.RecentSearches
import com.mk.newsshorts.core.data.local.SeenArticlesStore
import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.core.domain.OriginPreferenceStore
import com.mk.newsshorts.core.data.local.SettingsOriginPreferenceStore
import com.mk.newsshorts.core.data.local.SettingsStorage
import com.mk.newsshorts.core.data.platform.NoOpAnalyticsReporter
import com.mk.newsshorts.core.data.platform.NoOpAuthClient
import com.mk.newsshorts.core.data.platform.NoOpDeviceIntegrityInspector
import com.mk.newsshorts.core.data.platform.NoOpPushSubscriber
import com.mk.newsshorts.core.data.platform.NoOpRemoteSyncClient
import com.mk.newsshorts.core.data.remote.ApiConfig
import com.mk.newsshorts.core.data.remote.DefaultRemoteConfigClient
import com.mk.newsshorts.core.data.remote.OriginFailoverClient
import com.mk.newsshorts.core.domain.analytics.AnalyticsReporter
import com.mk.newsshorts.core.domain.auth.AuthClient
import com.mk.newsshorts.core.domain.config.RemoteConfigClient
import com.mk.newsshorts.core.domain.notifications.PushSubscriber
import com.mk.newsshorts.core.domain.security.DeviceIntegrityInspector
import com.mk.newsshorts.core.domain.sync.RemoteSyncClient
import com.mk.newsshorts.core.data.remote.NewsApiClient
import com.mk.newsshorts.core.data.remote.NotificationInboxClient
import com.mk.newsshorts.core.domain.notifications.NotificationInboxFeed
import com.mk.newsshorts.core.data.remote.SharePageResolver
import com.mk.newsshorts.core.data.remote.createHttpClient
import com.mk.newsshorts.core.data.repository.DefaultSavedArticlesRepository
import com.mk.newsshorts.core.data.repository.NewsRepositoryImpl
import com.mk.newsshorts.core.domain.saved.SavedArticles
import com.mk.newsshorts.core.domain.feed.DefaultFeedInvalidator
import com.mk.newsshorts.core.domain.feed.FeedInvalidator
import com.mk.newsshorts.core.domain.repository.ArticleLookup
import com.mk.newsshorts.core.domain.repository.InboxReadMarker
import com.mk.newsshorts.core.domain.repository.NewsRepository
import com.mk.newsshorts.navigation.DeepLinkBus
import com.mk.newsshorts.navigation.NotificationBus
import com.mk.newsshorts.navigation.SignInLinkBus
import org.koin.dsl.module

val dataModule = module {
    single<SettingsStorage>(createdAtStart = false) { platformSettingsStorage(getKoin()) }
    single<AnalyticsReporter>(createdAtStart = false) { NoOpAnalyticsReporter }
    single<PushSubscriber>(createdAtStart = false) { NoOpPushSubscriber }
    single<DeviceIntegrityInspector>(createdAtStart = false) { NoOpDeviceIntegrityInspector }
    single<AuthClient>(createdAtStart = false) { NoOpAuthClient }
    single<RemoteSyncClient>(createdAtStart = false) { NoOpRemoteSyncClient }
    // These are platform-to-app inboxes, not navigation; they live in
    // :core:navigation because composeApp, auth, and inbox all need them, and a
    // :core:eventbus module for three tiny files would be noise. Not lazy:
    // cold-start links and notifications can be posted before the ViewModels
    // that consume them exist, so their inboxes must already exist.
    single(createdAtStart = true) { DeepLinkBus() }
    single(createdAtStart = true) { NotificationBus() }
    single(createdAtStart = true) { SignInLinkBus() }
    single(createdAtStart = false) { createHttpClient() }
    single(createdAtStart = false) { ApiConfig() }
    single<OriginPreferenceStore>(createdAtStart = false) {
        SettingsOriginPreferenceStore(settingsStorage = get())
    }
    single(createdAtStart = false) {
        OriginFailoverClient(httpClient = get(), apiConfig = get(), preferenceStore = get())
    }
    single(createdAtStart = false) { NewsApiClient(originClient = get(), apiConfig = get()) }
    single<RemoteConfigClient>(createdAtStart = false) {
        DefaultRemoteConfigClient(originClient = get(), apiConfig = get())
    }
    single(createdAtStart = false) { SharePageResolver(httpClient = get()) }
    single<NotificationInboxFeed>(createdAtStart = false) {
        NotificationInboxClient(originClient = get(), apiConfig = get())
    }
    single(createdAtStart = false) { NewsLocalDataSource(settingsStorage = get()) }
    single<ArticleLookup>(createdAtStart = false) { get<NewsLocalDataSource>() }
    single(createdAtStart = false) { SettingsManager(settingsStorage = get()) }
    single(createdAtStart = false) { SavedArticlesStore(settingsStorage = get()) }
    single<SavedArticles>(createdAtStart = false) {
        DefaultSavedArticlesRepository(store = get<SavedArticlesStore>())
    }
    single(createdAtStart = false) { SeenArticlesStore(settingsStorage = get()) }
    single(createdAtStart = false) { NotificationInboxStore(settingsStorage = get()) }
    single<InboxReadMarker>(createdAtStart = false) { get<NotificationInboxStore>() }
    single<RecentSearches>(createdAtStart = false) {
        RecentSearchesStore(settingsStorage = get())
    }
    single(createdAtStart = false) { PendingSignInEmailStore(settingsStorage = get()) }
    single<PendingSignInEmailPersistence>(createdAtStart = false) {
        get<PendingSignInEmailStore>()
    }
    single<AuthSession>(createdAtStart = true) { DefaultAuthSession(authClient = get()) }
    single<FeedInvalidator>(createdAtStart = false) { DefaultFeedInvalidator() }
    single<NewsRepository>(createdAtStart = false) {
        NewsRepositoryImpl(
            newsApiClient = get(),
            localDataSource = get()
        )
    }
}
