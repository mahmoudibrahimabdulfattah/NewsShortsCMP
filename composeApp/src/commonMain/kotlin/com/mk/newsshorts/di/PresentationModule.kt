package com.mk.newsshorts.di

import com.mk.newsshorts.presentation.viewmodel.NewsViewModel
import org.koin.dsl.module

val presentationModule = module {
    single(createdAtStart = false) {
        NewsViewModel(
            getTopHeadlinesUseCase = get(),
            settingsManager = get(),
            analytics = get(),
            pushSubscriber = get(),
            deepLinkBus = get(),
            signInLinkBus = get(),
            savedArticlesStore = get(),
            seenArticlesStore = get(),
            pendingSignInEmailStore = get(),
            remoteConfigClient = get(),
            deviceIntegrityInspector = get(),
            authClient = get(),
            remoteSyncClient = get()
        )
    }
}

