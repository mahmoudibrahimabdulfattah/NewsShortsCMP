package com.mk.newsshorts.di

import com.mk.newsshorts.data.local.SettingsManager
import com.mk.newsshorts.feature.saved.SavedArticlesViewModel
import com.mk.newsshorts.feature.search.SearchViewModel
import com.mk.newsshorts.feature.settings.SettingsViewModel
import com.mk.newsshorts.presentation.viewmodel.NewsViewModel
import org.koin.dsl.module

val presentationModule = module {
    single(createdAtStart = false) {
        SavedArticlesViewModel(repository = get(), analytics = get())
    }
    single(createdAtStart = false) {
        SettingsViewModel(
            settingsManager = get<SettingsManager>(),
            analytics = get(),
            pushSubscriber = get(),
        )
    }
    single(createdAtStart = false) {
        SearchViewModel(
            searchNews = get(),
            recentSearches = get(),
            analytics = get(),
        )
    }
    single(createdAtStart = false) {
        NewsViewModel(
            getTopHeadlinesUseCase = get(),
            savedArticlesViewModel = get(),
            settingsViewModel = get(),
            settingsManager = get(),
            analytics = get(),
            pushSubscriber = get(),
            deepLinkBus = get(),
            sharePageResolver = get(),
            notificationInboxClient = get(),
            notificationInboxStore = get(),
            notificationBus = get(),
            signInLinkBus = get(),
            savedArticlesRepository = get(),
            seenArticlesStore = get(),
            pendingSignInEmailStore = get(),
            remoteConfigClient = get(),
            deviceIntegrityInspector = get(),
            authClient = get(),
            remoteSyncClient = get()
        )
    }
}
