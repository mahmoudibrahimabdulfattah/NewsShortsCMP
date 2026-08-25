package com.mk.newsshorts.di

import com.mk.newsshorts.presentation.viewmodel.NewsViewModel
import org.koin.dsl.module

val presentationModule = module {
    single(createdAtStart = false) {
        NewsViewModel(
            getTopHeadlinesUseCase = get(),
            searchNewsUseCase = get(),
            recentSearchesStore = get(),
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

